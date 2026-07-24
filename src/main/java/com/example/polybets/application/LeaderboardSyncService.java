package com.example.polybets.application;

import com.example.polybets.domain.model.ActivePosition;
import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.Trader;
import com.example.polybets.domain.port.ConsensusRepositoryPort;
import com.example.polybets.domain.port.LeaderboardPort;
import com.example.polybets.domain.port.PositionsPort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Use case: her kategori için top-N leaderboard'u ve her traderın aktif
 * pozisyonlarını çekip ConsensusRepositoryPort üzerinden kalıcı hale getirir.
 * Sadece portlara bağımlı — hangi API'nin ya da hangi veritabanının kullanıldığını bilmez.
 */
@Service
public class LeaderboardSyncService {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardSyncService.class);

    private final LeaderboardPort leaderboardPort;
    private final PositionsPort positionsPort;
    private final ConsensusRepositoryPort repositoryPort;
    private final int topN;
    private final boolean syncEnabled;

    public LeaderboardSyncService(
            LeaderboardPort leaderboardPort,
            PositionsPort positionsPort,
            ConsensusRepositoryPort repositoryPort,
            @Value("${polymarket.top-n}") int topN,
            @Value("${polymarket.sync.enabled}") boolean syncEnabled) {
        this.leaderboardPort = leaderboardPort;
        this.positionsPort = positionsPort;
        this.repositoryPort = repositoryPort;
        this.topN = topN;
        this.syncEnabled = syncEnabled;
    }

    @PostConstruct
    public void initialSync() {
        if (!syncEnabled) {
            log.info("Sync devre disi (polymarket.sync.enabled=false).");
            return;
        }
        for (Category category : Category.values()) {
            syncCategory(category);
        }
    }

    @Scheduled(cron = "${polymarket.sync.cron}")
    public void scheduledSync() {
        if (!syncEnabled) {
            return;
        }
        log.info("Zamanlanmis senkronizasyon basliyor...");
        for (Category category : Category.values()) {
            syncCategory(category);
        }
        log.info("Zamanlanmis senkronizasyon tamamlandi.");
    }

    public void syncCategory(Category category) {
        List<Trader> traders = leaderboardPort.fetchMonthlyLeaderboard(category, topN);
        if (traders.isEmpty()) {
            log.warn("Kategori {} icin leaderboard bos geldi, mevcut kayit korunuyor.", category);
            return;
        }

        List<ActivePosition> allPositions = new ArrayList<>();
        for (Trader trader : traders) {
            allPositions.addAll(positionsPort.fetchActivePositions(trader.proxyWallet()));
        }

        repositoryPort.saveSnapshot(category, traders, allPositions, Instant.now());
        log.info("Kategori {} senkronize edildi: {} trader, {} aktif pozisyon.",
                category, traders.size(), allPositions.size());
    }
}
