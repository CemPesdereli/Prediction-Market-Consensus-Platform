package com.example.polybets.domain.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Bir trader'ın, kendi kategori kohortu (top-20) içindeki ROI'sine göre
 * normalize edilmiş ağırlığı. weight her zaman [1.0, 3.0] aralığındadır.
 */
public record WeightedTrader(Trader trader, double weight) {

    /**
     * Kohort icindeki her trader icin [1.0, 3.0] araliginda ROI-normalize
     * agirlik hesaplar. Hem aktif (ConsensusService) hem kapanmis
     * (ClosedConsensusService) bahis hesaplamalari ayni agirligi kullanir --
     * "en basarili traderin oyu daha agir basar" tutarli olmali.
     *
     * weight_i = 1.0 + 2.0 * (roi_i - min(roi)) / (max(roi) - min(roi))
     */
    public static List<WeightedTrader> computeAll(List<Trader> traders) {
        double minRoi = traders.stream().mapToDouble(Trader::roi).min().orElse(0.0);
        double maxRoi = traders.stream().mapToDouble(Trader::roi).max().orElse(0.0);
        double range = maxRoi - minRoi;

        return traders.stream()
                .map(trader -> {
                    double normalized = range == 0.0 ? 0.5 : (trader.roi() - minRoi) / range;
                    double weight = 1.0 + 2.0 * normalized;
                    return new WeightedTrader(trader, weight);
                })
                .collect(Collectors.toList());
    }
}
