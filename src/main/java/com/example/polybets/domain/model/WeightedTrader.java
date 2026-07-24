package com.example.polybets.domain.model;

/**
 * Bir trader'ın, kendi kategori kohortu (top-20) içindeki ROI'sine göre
 * normalize edilmiş ağırlığı. weight her zaman [1.0, 3.0] aralığındadır.
 */
public record WeightedTrader(Trader trader, double weight) {
}
