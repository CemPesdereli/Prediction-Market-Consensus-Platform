package com.example.polybets.adapter.out.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET https://data-api.polymarket.com/v1/leaderboard yanıtındaki tek bir satır.
 * Bu paket adapter katmanına ait — domain bu sınıfı hiç bilmez.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LeaderboardEntryDto(
        String rank,
        String proxyWallet,
        String userName,
        Double vol,
        Double pnl,
        String profileImage,
        String xUsername,
        Boolean verifiedBadge
) {
}
