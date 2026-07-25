package com.example.polybets.adapter.in.web;

import com.example.polybets.application.ClosedConsensusService;
import com.example.polybets.application.ConsensusService;
import com.example.polybets.application.LeaderboardSyncService;
import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.ClosedConsensusMarket;
import com.example.polybets.domain.model.ConsensusMarket;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CommonBetsController {

    private final ConsensusService consensusService;
    private final ClosedConsensusService closedConsensusService;
    private final LeaderboardSyncService syncService;

    public CommonBetsController(
            ConsensusService consensusService,
            ClosedConsensusService closedConsensusService,
            LeaderboardSyncService syncService) {
        this.consensusService = consensusService;
        this.closedConsensusService = closedConsensusService;
        this.syncService = syncService;
    }

    /**
     * GET /api/common-bets?category=WEATHER
     * Hem klasik headcount (plainConsensusPercent) hem ROI-agirlikli
     * (weightedConsensusPercent) skorlari birlikte doner.
     */
    @GetMapping("/common-bets")
    public ResponseEntity<List<ConsensusMarket>> getCommonBets(
            @RequestParam(defaultValue = "POLITICS") Category category) {
        return ResponseEntity.ok(consensusService.getConsensus(category));
    }

    /**
     * GET /api/closed-bets?category=WEATHER
     * Top-20 kohortunun son polymarket.closed-window-days gün içinde
     * kapanmış (redeem edilmiş) ortak marketleri -- kim Yes/No demiş,
     * ne kadar kâr/zarar etmiş. Canlı (on-demand) hesaplanır, kalıcı değildir.
     */
    @GetMapping("/closed-bets")
    public ResponseEntity<List<ClosedConsensusMarket>> getClosedBets(
            @RequestParam(defaultValue = "POLITICS") Category category) {
        return ResponseEntity.ok(closedConsensusService.getClosedConsensus(category));
    }

    /**
     * POST /api/sync?category=WEATHER
     * 30 dakikalik zamanlanmis job'u beklemeden manuel yenileme (demo/test icin).
     */
    @PostMapping("/sync")
    public ResponseEntity<String> triggerSync(@RequestParam Category category) {
        syncService.syncCategory(category);
        return ResponseEntity.ok("Kategori senkronize edildi: " + category);
    }

    /**
     * GET /api/categories
     * Frontend'in kategori dropdown'unu doldurmasi icin.
     */
    @GetMapping("/categories")
    public ResponseEntity<Category[]> getCategories() {
        return ResponseEntity.ok(Category.values());
    }
}
