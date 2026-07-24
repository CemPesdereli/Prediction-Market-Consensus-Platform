package com.example.polybets.adapter.out.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET https://data-api.polymarket.com/positions?user=0x... yanıtındaki tek bir pozisyon.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionDto(
        String proxyWallet,
        String asset,
        String conditionId,
        Double size,
        Double avgPrice,
        Double initialValue,
        Double currentValue,
        Double cashPnl,
        Double percentPnl,
        Double curPrice,
        Boolean redeemable,
        Boolean mergeable,
        String title,
        String slug,
        String icon,
        String eventSlug,
        String outcome,
        Integer outcomeIndex,
        String endDate,
        Boolean negativeRisk
) {
}
