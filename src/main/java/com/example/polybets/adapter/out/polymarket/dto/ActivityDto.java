package com.example.polybets.adapter.out.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET https://data-api.polymarket.com/activity?user=0x...&type=REDEEM yanıtındaki
 * tek bir on-chain aktivite kaydı. Kazanılmış (redeem edilmiş) pozisyonları
 * yakalamak için kullanılıyor -- redeem sonrası pozisyon /positions'tan tamamen
 * kayboluyor, tek iz burada kalıyor.
 *
 * Aynı DTO, type=TRADE sorgusuyla o markette yapılmış tüm alım/satım (BUY/SELL)
 * kayıtlarını çekmek için de kullanılıyor (bkz. PolymarketPositionsAdapter) --
 * kazanan bir pozisyonun gerçek net kârını hesaplamak için gereken price/side alanları
 * sadece TRADE kayıtlarında dolu gelir, REDEEM kayıtlarında boş/0 gelir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityDto(
        String proxyWallet,
        Long timestamp,
        String conditionId,
        String type,
        Double size,
        Double usdcSize,
        Double price,
        String side,
        String title,
        String slug,
        String eventSlug,
        String outcome
) {
}
