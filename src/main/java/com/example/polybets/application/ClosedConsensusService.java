package com.example.polybets.application;

import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.ClosedConsensusMarket;
import com.example.polybets.domain.model.ClosedPosition;
import com.example.polybets.domain.model.Trader;
import com.example.polybets.domain.model.WeightedTrader;
import com.example.polybets.domain.port.ConsensusRepositoryPort;
import com.example.polybets.domain.port.PositionsPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Top-20 kohortunun son N gün içinde kapanmış (redeem edilmiş) ortak
 * pozisyonlarını gösterir: kim Yes/No demiş, ne kadar kâr/zarar etmiş.
 *
 * Trader listesi DB'deki en son senkronize edilmiş leaderboard'dan gelir
 * (ConsensusRepositoryPort), ama kapanmış pozisyonlar kalıcı olarak
 * saklanmaz -- "kapanmış bahisleri göster" butonuna basıldığında canlı
 * (on-demand) çekilip hesaplanır.
 */
@Service
public class ClosedConsensusService {

    private final ConsensusRepositoryPort repositoryPort;
    private final PositionsPort positionsPort;
    private final int minCommonHolders;
    private final int closedWindowDays;

    public ClosedConsensusService(
            ConsensusRepositoryPort repositoryPort,
            PositionsPort positionsPort,
            @Value("${polymarket.min-common-holders}") int minCommonHolders,
            @Value("${polymarket.closed-window-days}") int closedWindowDays) {
        this.repositoryPort = repositoryPort;
        this.positionsPort = positionsPort;
        this.minCommonHolders = minCommonHolders;
        this.closedWindowDays = closedWindowDays;
    }

    public List<ClosedConsensusMarket> getClosedConsensus(Category category) {
        List<Trader> traders = repositoryPort.findLatestTraders(category);
        if (traders.isEmpty()) {
            return List.of();
        }

        Instant now = Instant.now();
        Instant windowStart = now.minus(closedWindowDays, ChronoUnit.DAYS);

        // Iki kaynak birlestiriliyor: /positions?redeemable=true (pratikte neredeyse
        // tamami KAYBEDILEN, henuz claim edilmemis bahisler) + REDEEM aktivitesi
        // (pratikte neredeyse tamami KAZANILAN bahisler -- claim edilen pozisyon
        // /positions'tan tamamen kayboluyor, tek izi burasi). Sadece birini kullanmak
        // sistematik olarak kazananlari ya da kaybedenleri disarida birakiyor.
        List<ClosedPosition> closedPositions = traders.parallelStream()
                .flatMap(t -> Stream.concat(
                        positionsPort.fetchClosedPositions(t.proxyWallet()).stream(),
                        positionsPort.fetchRedeemedPositions(t.proxyWallet(), windowStart).stream()))
                .collect(Collectors.toList());

        return calculate(traders, closedPositions, minCommonHolders, closedWindowDays, now);
    }

    /**
     * Saf hesaplama fonksiyonu. I/O yok, side-effect yok -- unit testte
     * doğrudan çağrılabilir.
     */
    public List<ClosedConsensusMarket> calculate(
            List<Trader> traders, List<ClosedPosition> positions, int minHolders, int windowDays, Instant now) {

        if (traders.isEmpty()) {
            return List.of();
        }

        Map<String, String> userNameByWallet = traders.stream()
                .collect(Collectors.toMap(Trader::proxyWallet, Trader::userName, (a, b) -> a));
        // Aktif consensus'taki ile ayni ROI-agirlikli formul (bkz. WeightedTrader) --
        // "en basarili traderin oyu daha agir basar" fikri kapanmis bahislerde de tutarli.
        Map<String, Double> weightByWallet = WeightedTrader.computeAll(traders).stream()
                .collect(Collectors.toMap(wt -> wt.trader().proxyWallet(), WeightedTrader::weight));
        int cohortSize = traders.size();
        Instant windowStart = now.minus(windowDays, ChronoUnit.DAYS);

        Map<String, List<ClosedPosition>> byMarket = positions.stream()
                .filter(p -> isWithinWindow(p.endDate(), windowStart, now))
                .collect(Collectors.groupingBy(ClosedPosition::conditionId, LinkedHashMap::new, Collectors.toList()));

        return byMarket.values().stream()
                .filter(list -> distinctWallets(list).size() >= minHolders)
                .map(list -> toClosedConsensusMarket(list, userNameByWallet, weightByWallet, cohortSize))
                .sorted(Comparator.comparingInt(ClosedConsensusMarket::holderCount).reversed())
                .collect(Collectors.toList());
    }

    private boolean isWithinWindow(String endDate, Instant windowStart, Instant now) {
        Instant end = parseEndDate(endDate);
        if (end == null) {
            return false;
        }
        return !end.isBefore(windowStart) && !end.isAfter(now);
    }

    /**
     * endDate iki farkli formatta gelebiliyor: /positions'tan gelenler sadece
     * tarih ("2026-07-23", saat yok), REDEEM aktivitesinden sentezlenenler tam
     * ISO instant ("2026-07-23T23:47:43Z"). Once Instant.parse denenir, olmazsa
     * bare-date olarak (gunun basi, UTC) parse edilir. ONEMLI: bu ikinci yol
     * olmadan /positions kaynakli TUM kayitlar (pratikte kaybedenlerin tamami)
     * sessizce pencere disi sayilip eleniyordu.
     */
    private Instant parseEndDate(String endDate) {
        if (endDate == null) {
            return null;
        }
        try {
            return Instant.parse(endDate);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(endDate).atStartOfDay(ZoneOffset.UTC).toInstant();
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private Set<String> distinctWallets(List<ClosedPosition> list) {
        return list.stream().map(ClosedPosition::proxyWallet).collect(Collectors.toSet());
    }

    private ClosedConsensusMarket toClosedConsensusMarket(
            List<ClosedPosition> positionsInMarket,
            Map<String, String> userNameByWallet,
            Map<String, Double> weightByWallet,
            int cohortSize) {

        // Ayni cuzdan ayni markette birden fazla outcome/asset'te pozisyon tutmus olabilir;
        // holder basina en yuksek |cashPnl| degerine sahip olani aliyoruz.
        Map<String, ClosedPosition> dedupedByWallet = positionsInMarket.stream()
                .collect(Collectors.toMap(
                        ClosedPosition::proxyWallet,
                        p -> p,
                        (a, b) -> Math.abs(valueOrZero(a.cashPnl())) >= Math.abs(valueOrZero(b.cashPnl())) ? a : b));

        List<ClosedConsensusMarket.HolderOutcome> holders = dedupedByWallet.values().stream()
                .map(p -> new ClosedConsensusMarket.HolderOutcome(
                        userNameByWallet.get(p.proxyWallet()),
                        p.proxyWallet(),
                        p.outcome(),
                        p.won(),
                        p.cashPnl(),
                        p.percentPnl(),
                        p.spentValue(),
                        weightByWallet.getOrDefault(p.proxyWallet(), 0.0)))
                // Once kazananlar (won=true), sonra kaybedenler kayip buyuklugune gore
                .sorted(Comparator
                        .comparing(ClosedConsensusMarket.HolderOutcome::won).reversed()
                        .thenComparing(Comparator.comparingDouble(
                                (ClosedConsensusMarket.HolderOutcome h) -> valueOrZero(h.cashPnl())).reversed()))
                .collect(Collectors.toList());

        ClosedPosition first = positionsInMarket.get(0);
        return new ClosedConsensusMarket(
                first.conditionId(),
                first.marketTitle(),
                first.marketSlug(),
                first.eventSlug(),
                first.endDate(),
                dedupedByWallet.size(),
                cohortSize,
                holders);
    }

    private double valueOrZero(Double value) {
        return value == null ? 0.0 : value;
    }
}
