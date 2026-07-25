package com.example.polybets.application;

import com.example.polybets.domain.model.ActivePosition;
import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.ConsensusMarket;
import com.example.polybets.domain.model.Trader;
import com.example.polybets.domain.model.WeightedTrader;
import com.example.polybets.domain.port.ConsensusRepositoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Top-20 kohortundaki traderların ROI'sine göre ağırlıklandırılmış consensus
 * hesaplamasını yapar.
 *
 * Algoritma:
 *  1) Her trader için roi = pnl / vol hesaplanır (Trader.roi()).
 *  2) Kohort içinde min-max normalize edilip [1.0, 3.0] araligina sikistirilir:
 *       weight_i = 1.0 + 2.0 * (roi_i - min(roi)) / (max(roi) - min(roi))
 *     Boylece en kotu ROI'li bile 1.0x taban agirlik tasir (top-20'ye girmis
 *     olmanin karsiligi), en iyi ROI'li en fazla 3x agirlikli olur (tek bir
 *     "balina" sonucu tek basina domine edemez).
 *  3) Ayni conditionId'yi paylasan traderlar gruplanir; grup icin:
 *       weightedConsensusPercent = sum(weight_i in grup) / sum(weight_i tum kohort) * 100
 *       sentimentYesPercent      = yesWeight / (yesWeight + noWeight) * 100
 *
 * calculate() metodu saf (pure) fonksiyondur -- hicbir I/O yapmaz, bu yuzden
 * repository/mock kullanmadan dogrudan unit test edilebilir.
 */
@Service
public class ConsensusService {

    private final ConsensusRepositoryPort repositoryPort;
    private final int minCommonHolders;

    public ConsensusService(
            ConsensusRepositoryPort repositoryPort,
            @Value("${polymarket.min-common-holders}") int minCommonHolders) {
        this.repositoryPort = repositoryPort;
        this.minCommonHolders = minCommonHolders;
    }

    public List<ConsensusMarket> getConsensus(Category category) {
        List<Trader> traders = repositoryPort.findLatestTraders(category);
        List<ActivePosition> positions = repositoryPort.findLatestPositions(category);
        return calculate(traders, positions, minCommonHolders);
    }

    /**
     * Saf hesaplama fonksiyonu. I/O yok, side-effect yok -- unit testte
     * doğrudan çağrılabilir.
     */
    public List<ConsensusMarket> calculate(List<Trader> traders, List<ActivePosition> positions, int minHolders) {
        if (traders.isEmpty()) {
            return List.of();
        }

        List<WeightedTrader> weightedTraders = WeightedTrader.computeAll(traders);
        Map<String, Double> weightByWallet = weightedTraders.stream()
                .collect(Collectors.toMap(wt -> wt.trader().proxyWallet(), WeightedTrader::weight));
        Map<String, String> userNameByWallet = traders.stream()
                .collect(Collectors.toMap(Trader::proxyWallet, Trader::userName, (a, b) -> a));

        double totalCohortWeight = weightedTraders.stream().mapToDouble(WeightedTrader::weight).sum();
        int cohortSize = traders.size();
        List<Double> sortedCohortWeightsDesc = weightedTraders.stream()
                .map(WeightedTrader::weight)
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        Map<String, List<ActivePosition>> byMarket = positions.stream()
                .collect(Collectors.groupingBy(ActivePosition::conditionId, LinkedHashMap::new, Collectors.toList()));

        return byMarket.values().stream()
                .filter(list -> distinctWallets(list).size() >= minHolders)
                .map(list -> toConsensusMarket(list, weightByWallet, userNameByWallet, totalCohortWeight, cohortSize, sortedCohortWeightsDesc))
                .sorted(Comparator.comparingDouble(ConsensusMarket::weightedConsensusPercent).reversed())
                .collect(Collectors.toList());
    }

    private Set<String> distinctWallets(List<ActivePosition> list) {
        return list.stream().map(ActivePosition::proxyWallet).collect(Collectors.toSet());
    }

    private ConsensusMarket toConsensusMarket(
            List<ActivePosition> positionsInMarket,
            Map<String, Double> weightByWallet,
            Map<String, String> userNameByWallet,
            double totalCohortWeight,
            int cohortSize,
            List<Double> sortedCohortWeightsDesc) {

        // Ayni cuzdan ayni markette birden fazla outcome/asset'te pozisyon tutabilir;
        // holder listesi icin cuzdan basina en yuksek currentValue'ya sahip olani aliyoruz.
        Map<String, ActivePosition> dedupedByWallet = positionsInMarket.stream()
                .collect(Collectors.toMap(
                        ActivePosition::proxyWallet,
                        p -> p,
                        (a, b) -> valueOrZero(a.currentValue()) >= valueOrZero(b.currentValue()) ? a : b));

        double groupWeight = dedupedByWallet.keySet().stream()
                .mapToDouble(wallet -> weightByWallet.getOrDefault(wallet, 0.0))
                .sum();

        double yesWeight = 0.0;
        double noWeight = 0.0;
        for (Map.Entry<String, ActivePosition> entry : dedupedByWallet.entrySet()) {
            double w = weightByWallet.getOrDefault(entry.getKey(), 0.0);
            String outcome = entry.getValue().outcome();
            if (outcome != null && outcome.equalsIgnoreCase("yes")) {
                yesWeight += w;
            } else if (outcome != null && outcome.equalsIgnoreCase("no")) {
                noWeight += w;
            }
        }
        Double sentimentYesPercent = (yesWeight + noWeight) == 0.0
                ? null
                : (yesWeight / (yesWeight + noWeight)) * 100.0;

        List<ConsensusMarket.HolderDetail> holders = dedupedByWallet.values().stream()
                .map(p -> new ConsensusMarket.HolderDetail(
                        userNameByWallet.get(p.proxyWallet()),
                        p.proxyWallet(),
                        p.outcome(),
                        p.curPrice(),
                        p.avgPrice(),
                        p.currentValue(),
                        weightByWallet.getOrDefault(p.proxyWallet(), 0.0)))
                .sorted(Comparator.comparingDouble(ConsensusMarket.HolderDetail::weight).reversed())
                .collect(Collectors.toList());

        ActivePosition first = positionsInMarket.get(0);
        double weightedConsensusPercent = totalCohortWeight == 0.0 ? 0.0 : (groupWeight / totalCohortWeight) * 100.0;

        int holderCount = dedupedByWallet.size();
        double maxPossiblePercent = totalCohortWeight == 0.0
                ? 0.0
                : (sumTopK(sortedCohortWeightsDesc, holderCount) / totalCohortWeight) * 100.0;
        double minPossiblePercent = totalCohortWeight == 0.0
                ? 0.0
                : (sumBottomK(sortedCohortWeightsDesc, holderCount) / totalCohortWeight) * 100.0;

        return new ConsensusMarket(
                first.conditionId(),
                first.marketTitle(),
                first.marketSlug(),
                first.eventSlug(),
                first.endDate(),
                holderCount,
                cohortSize,
                weightedConsensusPercent,
                minPossiblePercent,
                maxPossiblePercent,
                sentimentYesPercent,
                holders);
    }

    /**
     * Sirali (buyukten kucuge) agirlik listesinde en yuksek k agirligin toplami.
     * "Bu kadar kisi tutsaydi en fazla alabilecegi skor" -- en iyi k trader varsayimi.
     */
    private double sumTopK(List<Double> sortedDesc, int k) {
        return sortedDesc.stream().limit(k).mapToDouble(Double::doubleValue).sum();
    }

    /**
     * Sirali (buyukten kucuge) agirlik listesinde en dusuk k agirligin toplami.
     * "Bu kadar kisi tutsaydi en az alabilecegi skor" -- en kotu k trader varsayimi.
     */
    private double sumBottomK(List<Double> sortedDesc, int k) {
        return sortedDesc.stream().skip(Math.max(0, sortedDesc.size() - k)).mapToDouble(Double::doubleValue).sum();
    }

    private double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
