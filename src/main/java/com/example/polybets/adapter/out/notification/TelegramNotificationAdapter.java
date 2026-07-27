package com.example.polybets.adapter.out.notification;

import com.example.polybets.domain.model.WatchedBet;
import com.example.polybets.domain.port.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.Locale;

/**
 * NotificationPort'un Telegram Bot API implementasyonu.
 * POST https://api.telegram.org/bot{token}/sendMessage
 *
 * telegram.bot-token / telegram.chat-id boşsa (henüz bir bot kurulmadıysa)
 * adapter sessizce devre dışı kalır -- alarm hâlâ TRIGGERED olarak işaretlenir
 * ve loglanır, sadece dışarı bildirim gitmez. Böylece Telegram kurulumu
 * yapılmadan da özelliğin geri kalanı (izleme/tetikleme mantığı) test edilebilir.
 */
@Component
public class TelegramNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationAdapter.class);

    private final WebClient telegramWebClient;
    private final String botToken;
    private final String chatId;
    private final boolean enabled;

    public TelegramNotificationAdapter(
            WebClient telegramWebClient,
            @Value("${telegram.bot-token:}") String botToken,
            @Value("${telegram.chat-id:}") String chatId) {
        this.telegramWebClient = telegramWebClient;
        this.botToken = botToken;
        this.chatId = chatId;
        this.enabled = !botToken.isBlank() && !chatId.isBlank();
        if (!enabled) {
            log.warn("Telegram bot-token/chat-id ayarlanmamis, fiyat alarmlari sadece loglanacak.");
        }
    }

    @Override
    public void sendPriceAlert(WatchedBet watchedBet, double currentPrice) {
        if (!enabled) {
            log.info("[Telegram devre disi] Alarm tetiklendi: {}", buildMessage(watchedBet, currentPrice));
            return;
        }
        try {
            telegramWebClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/bot{token}/sendMessage").build(botToken))
                    .bodyValue(Map.of(
                            "chat_id", chatId,
                            "text", buildMessage(watchedBet, currentPrice)))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception e) {
            log.warn("Telegram bildirimi gonderilemedi (watchedBetId={}): {}", watchedBet.id(), e.getMessage());
        }
    }

    private String buildMessage(WatchedBet w, double currentPrice) {
        return String.format(Locale.ROOT,
                "🔔 Fiyat alarmi tetiklendi!\n\n%s\n%s: %.1f¢ (giris: %.1f¢, hedef: %.1f¢)%s",
                w.marketTitle(), w.outcome(), currentPrice * 100, w.entryPrice() * 100, w.targetPrice() * 100,
                w.eventSlug() != null ? "\nhttps://polymarket.com/event/" + w.eventSlug() : "");
    }
}
