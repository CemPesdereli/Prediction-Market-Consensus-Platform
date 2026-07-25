package com.example.polybets.domain.model;

import java.util.List;

/**
 * Top-20 kohortunun, belirlenen zaman penceresi (ör. son 3 gün) içinde
 * kapanmış (redeem edilmiş) ortak marketi. Kim Yes/No demiş, ne kadar
 * kâr/zarar etmiş bilgisini taşır -- weighted skorlama yapılmaz, bu saf
 * bir detay/döküm görünümüdür.
 */
public record ClosedConsensusMarket(
        String conditionId,
        String marketTitle,
        String marketSlug,
        String eventSlug,
        String endDate,
        int holderCount,
        int cohortSize,
        List<HolderOutcome> holders
) {
    public double plainConsensusPercent() {
        return cohortSize == 0 ? 0.0 : (holderCount * 100.0) / cohortSize;
    }

    /**
     * won/cashPnl her holder için ayrı ayrı taşınıyor (bkz. ClosedPosition javadoc)
     * -- market seviyesinde tek bir "toplam kâr/zarar" göstermek bilinçli olarak
     * yapılmıyor: kazananların net kârı bilinmediği için toplam yanıltıcı olurdu
     * (örn. herkes kazandığında "+0$" gibi görünüp "kimse kazanmadı" izlenimi verir).
     * Frontend, kazanan/kaybeden sayısını ve bilinen (kaybeden) toplam zararı bu
     * listeden kendisi türetiyor.
     */
    public record HolderOutcome(
            String userName,
            String proxyWallet,
            String outcome,
            boolean won,
            Double cashPnl,
            Double percentPnl,
            double weight
    ) {
    }
}
