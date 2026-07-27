package com.example.polybets.domain.model;

/**
 * Belirli bir cüzdana bağlı olmadan, tek bir marketin (conditionId) o anki
 * halini taşır -- WatchedBet oluştururken slug'dan market bilgisini
 * çözümlemek ve fiyat alarmı kontrolünde anlık fiyatı okumak için kullanılır.
 */
public record MarketSnapshot(
        String conditionId,
        String title,
        String slug,
        String eventSlug,
        String endDate,
        Double yesPrice,
        Double noPrice
) {
    public Double priceForOutcome(String outcome) {
        if (outcome == null) {
            return null;
        }
        if (outcome.equalsIgnoreCase("yes")) {
            return yesPrice;
        }
        if (outcome.equalsIgnoreCase("no")) {
            return noPrice;
        }
        return null;
    }
}
