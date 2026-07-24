package com.example.polybets.domain.model;

/**
 * Polymarket /v1/leaderboard endpoint'inin resmi olarak desteklediği kategoriler.
 * Domain katmanında framework bağımlılığı yok.
 */
public enum Category {
    OVERALL,
    POLITICS,
    SPORTS,
    ESPORTS,
    CRYPTO,
    CULTURE,
    MENTIONS,
    WEATHER,
    ECONOMICS,
    TECH,
    FINANCE
}
