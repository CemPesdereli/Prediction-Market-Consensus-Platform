package com.example.polybets.adapter.out.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET https://data-api.polymarket.com/activity?user=0x...&type=REDEEM yanıtındaki
 * tek bir on-chain aktivite kaydı. Kazanılmış (redeem edilmiş) pozisyonları
 * yakalamak için kullanılıyor -- redeem sonrası pozisyon /positions'tan tamamen
 * kayboluyor, tek iz burada kalıyor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityDto(
        String proxyWallet,
        Long timestamp,
        String conditionId,
        String type,
        Double size,
        Double usdcSize,
        String title,
        String slug,
        String eventSlug,
        String outcome
) {
}
