package com.example.polybets.adapter.out.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * GET https://gamma-api.polymarket.com/markets yanıtındaki tek bir market.
 * ÖNEMLİ: {@code outcomes} ve {@code outcomePrices} düz JSON dizisi değil,
 * JSON-encode edilmiş birer string olarak geliyor (ör. {@code "[\"Yes\", \"No\"]"})
 * -- Gamma API'nin bilinen bir tuhaflığı, adapter içinde ayrıca parse ediliyor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GammaMarketDto(
        String conditionId,
        String question,
        String slug,
        String outcomes,
        String outcomePrices,
        String clobTokenIds,
        String endDate,
        List<GammaEventDto> events
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GammaEventDto(String slug) {
    }
}
