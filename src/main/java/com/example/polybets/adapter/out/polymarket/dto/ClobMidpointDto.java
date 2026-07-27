package com.example.polybets.adapter.out.polymarket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GET https://clob.polymarket.com/midpoint?token_id=... yanıtı.
 * {@code mid} bid/ask ortalaması, sayı değil string olarak geliyor (ör. "0.575").
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClobMidpointDto(String mid) {
}
