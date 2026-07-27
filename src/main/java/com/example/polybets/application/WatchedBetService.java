package com.example.polybets.application;

import com.example.polybets.domain.model.MarketSnapshot;
import com.example.polybets.domain.model.WatchedBet;
import com.example.polybets.domain.model.WatchedBetStatus;
import com.example.polybets.domain.port.MarketPricePort;
import com.example.polybets.domain.port.NotificationPort;
import com.example.polybets.domain.port.WatchedBetRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Use case: kullanıcının kendi girdiği bahisler için manuel fiyat alarmı
 * kurması (create/list/cancel) ve bu alarmların periyodik olarak kontrol
 * edilip hedef fiyata ulaşıldığında NotificationPort üzerinden haber
 * verilmesi (checkPriceAlerts). Sadece portlara bağımlı.
 */
@Service
public class WatchedBetService {

    private static final Logger log = LoggerFactory.getLogger(WatchedBetService.class);

    private final WatchedBetRepositoryPort repositoryPort;
    private final MarketPricePort marketPricePort;
    private final NotificationPort notificationPort;
    private final boolean checkEnabled;

    public WatchedBetService(
            WatchedBetRepositoryPort repositoryPort,
            MarketPricePort marketPricePort,
            NotificationPort notificationPort,
            @Value("${polymarket.watched-bet.check.enabled}") boolean checkEnabled) {
        this.repositoryPort = repositoryPort;
        this.marketPricePort = marketPricePort;
        this.notificationPort = notificationPort;
        this.checkEnabled = checkEnabled;
    }

    public WatchedBet create(String marketSlug, String outcome, double entryPrice, double targetPrice) {
        String normalizedOutcome = normalizeOutcome(outcome);
        validatePrice(entryPrice, "entryPrice");
        validatePrice(targetPrice, "targetPrice");

        MarketSnapshot snapshot = marketPricePort.fetchMarketBySlug(marketSlug)
                .orElseThrow(() -> new IllegalArgumentException("Market bulunamadı (slug=" + marketSlug + ")"));

        Instant now = Instant.now();
        WatchedBet watchedBet = new WatchedBet(
                null,
                snapshot.conditionId(),
                snapshot.title(),
                snapshot.slug(),
                snapshot.eventSlug(),
                normalizedOutcome,
                entryPrice,
                targetPrice,
                WatchedBetStatus.ACTIVE,
                now,
                null,
                snapshot.priceForOutcome(normalizedOutcome),
                now);

        return repositoryPort.save(watchedBet);
    }

    public List<WatchedBet> listAll() {
        return repositoryPort.findAll();
    }

    public WatchedBet cancel(Long id) {
        WatchedBet watchedBet = repositoryPort.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Alarm bulunamadı (id=" + id + ")"));
        return repositoryPort.save(watchedBet.cancelled());
    }

    @Scheduled(cron = "${polymarket.watched-bet.check.cron}")
    public void scheduledCheck() {
        if (!checkEnabled) {
            return;
        }
        checkPriceAlerts();
    }

    /**
     * Kontrolun gercek mantigi -- polymarket.watched-bet.check.enabled=false
     * olsa bile dogrudan cagrilabilir (bkz. WatchedBetController POST /check),
     * tipki LeaderboardSyncService.syncCategory()'nin /api/sync icin enabled
     * flag'ini atlamasi gibi (bkz. CLAUDE.md).
     */
    public void checkPriceAlerts() {
        List<WatchedBet> active = repositoryPort.findByStatus(WatchedBetStatus.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        log.info("{} aktif fiyat alarmi kontrol ediliyor...", active.size());
        for (WatchedBet watchedBet : active) {
            checkOne(watchedBet);
        }
    }

    private void checkOne(WatchedBet watchedBet) {
        marketPricePort.fetchMarketByConditionId(watchedBet.conditionId())
                .map(snapshot -> snapshot.priceForOutcome(watchedBet.outcome()))
                .ifPresentOrElse(
                        currentPrice -> applyPrice(watchedBet, currentPrice),
                        () -> log.warn("Market {} icin anlik fiyat alinamadi, alarm {} atlaniyor.",
                                watchedBet.conditionId(), watchedBet.id()));
    }

    private void applyPrice(WatchedBet watchedBet, double currentPrice) {
        Instant now = Instant.now();
        WatchedBet checked = watchedBet.checked(currentPrice, now);
        if (checked.isTargetReached(currentPrice)) {
            WatchedBet triggered = checked.triggered(now);
            repositoryPort.save(triggered);
            notificationPort.sendPriceAlert(triggered, currentPrice);
            log.info("Fiyat alarmi tetiklendi: {} ({}) hedef={} anlik={}",
                    triggered.marketTitle(), triggered.outcome(), triggered.targetPrice(), currentPrice);
        } else {
            repositoryPort.save(checked);
        }
    }

    private String normalizeOutcome(String outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome bos olamaz");
        }
        if (outcome.equalsIgnoreCase("yes")) {
            return "Yes";
        }
        if (outcome.equalsIgnoreCase("no")) {
            return "No";
        }
        throw new IllegalArgumentException("outcome sadece YES ya da NO olabilir: " + outcome);
    }

    private void validatePrice(double price, String field) {
        if (price <= 0.0 || price > 1.0) {
            throw new IllegalArgumentException(field + " 0 ile 1 arasinda olmali (cent karsiligi): " + price);
        }
    }
}
