package com.example.polybets.domain.model;

/**
 * Bir trader'ın artık sonuçlanmış kapanmış pozisyonu. {@code won} pozisyonun
 * kazanıp kazanmadığını kaynağına göre kesin olarak taşır (kaynak bilgisiyle
 * belirleniyor -- bkz. PolymarketPositionsAdapter). cashPnl/percentPnl
 * kaybedenler için her zaman doludur; kazananlar için bilinmiyorsa null'dur
 * (net kârı hesaplamak maliyet geçmişini gerektirir, bkz. ClosedConsensusService).
 */
public record ClosedPosition(
        String proxyWallet,
        String conditionId,
        String marketTitle,
        String marketSlug,
        String eventSlug,
        String outcome,
        boolean won,
        Double cashPnl,
        Double percentPnl,
        String endDate
) {
}
