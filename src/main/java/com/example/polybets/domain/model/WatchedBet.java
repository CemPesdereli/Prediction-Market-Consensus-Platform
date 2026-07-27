package com.example.polybets.domain.model;

import java.time.Instant;

/**
 * Kullanıcının kendi girdiği bir bahis için tuttuğu manuel fiyat alarmı.
 * {@code entryPrice}/{@code targetPrice} 0-1 arası ondalık (diğer domain
 * modellerindeki curPrice/avgPrice ile aynı birim, "cent" gösterimi sadece
 * frontend'de ×100 olarak yapılıyor).
 *
 * Yön (artış mı düşüş mü bekleniyor) ayrı bir alan olarak tutulmuyor --
 * {@code targetPrice}'ın {@code entryPrice}'a göre büyük/küçük olması yeterli:
 * hedef girişten yüksekse "şu fiyata çıkınca haber ver" (kâr al), düşükse
 * "şu fiyata inince haber ver" (zarar kes/ucuzlama) anlamına gelir.
 */
public record WatchedBet(
        Long id,
        String conditionId,
        String marketTitle,
        String marketSlug,
        String eventSlug,
        String outcome,
        double entryPrice,
        double targetPrice,
        WatchedBetStatus status,
        Instant createdAt,
        Instant triggeredAt,
        Double lastCheckedPrice,
        Instant lastCheckedAt
) {
    public boolean isTargetReached(double currentPrice) {
        return targetPrice >= entryPrice ? currentPrice >= targetPrice : currentPrice <= targetPrice;
    }

    public WatchedBet checked(double currentPrice, Instant checkedAt) {
        return new WatchedBet(id, conditionId, marketTitle, marketSlug, eventSlug, outcome,
                entryPrice, targetPrice, status, createdAt, triggeredAt, currentPrice, checkedAt);
    }

    public WatchedBet triggered(Instant triggeredAt) {
        return new WatchedBet(id, conditionId, marketTitle, marketSlug, eventSlug, outcome,
                entryPrice, targetPrice, WatchedBetStatus.TRIGGERED, createdAt, triggeredAt,
                lastCheckedPrice, lastCheckedAt);
    }

    public WatchedBet cancelled() {
        return new WatchedBet(id, conditionId, marketTitle, marketSlug, eventSlug, outcome,
                entryPrice, targetPrice, WatchedBetStatus.CANCELLED, createdAt, triggeredAt,
                lastCheckedPrice, lastCheckedAt);
    }
}
