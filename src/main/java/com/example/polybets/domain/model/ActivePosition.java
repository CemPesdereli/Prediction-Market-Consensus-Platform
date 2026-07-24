package com.example.polybets.domain.model;

/**
 * Bir trader'ın şu anki (henüz redeem edilmemiş) aktif pozisyonu.
 */
public record ActivePosition(
        String proxyWallet,
        String conditionId,
        String marketTitle,
        String marketSlug,
        String eventSlug,
        String outcome,
        Double curPrice,
        Double currentValue,
        String endDate
) {
}
