package com.example.polybets.domain.port;

import com.example.polybets.domain.model.WatchedBet;

/**
 * Bir fiyat alarmı tetiklendiğinde kullanıcıya haber verilmesi. İlk
 * implementasyon Telegram bot üzerinden, ama port bunu bilmez -- ileride
 * e-posta/Discord gibi başka kanallar eklenmek istenirse yeni bir adapter
 * yazmak yeterli olur.
 */
public interface NotificationPort {

    void sendPriceAlert(WatchedBet watchedBet, double currentPrice);
}
