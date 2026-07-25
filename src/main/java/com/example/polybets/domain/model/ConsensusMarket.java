package com.example.polybets.domain.model;

import java.util.List;

/**
 * Top-20 kohortu içinde en az {minCommonHolders} traderın aynı piyasada
 * (conditionId) aktif pozisyonu olduğunda üretilen "ortak bahis" kaydı.
 * Hem klasik headcount hem ROI-ağırlıklı skorları birlikte taşır.
 */
public record ConsensusMarket(
        String conditionId,
        String marketTitle,
        String marketSlug,
        String eventSlug,
        String endDate,
        int holderCount,
        int cohortSize,
        double weightedConsensusPercent,
        double minPossiblePercent,
        double maxPossiblePercent,
        Double sentimentYesPercent,
        List<HolderDetail> holders
) {
    /**
     * Klasik (ağırlıksız) consensus yüzdesi: holderCount / cohortSize * 100.
     */
    public double plainConsensusPercent() {
        return cohortSize == 0 ? 0.0 : (holderCount * 100.0) / cohortSize;
    }

    public record HolderDetail(
            String userName,
            String proxyWallet,
            String outcome,
            Double curPrice,
            Double avgPrice,
            Double currentValue,
            double weight
    ) {
    }
}
