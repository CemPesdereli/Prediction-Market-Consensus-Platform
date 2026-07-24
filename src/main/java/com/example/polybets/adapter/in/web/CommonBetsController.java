package com.example.polybets.adapter.in.web;

import com.example.polybets.application.ConsensusService;
import com.example.polybets.application.LeaderboardSyncService;
import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.ConsensusMarket;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CommonBetsController {

    private final ConsensusService consensusService;
    private final LeaderboardSyncService syncService;

    public CommonBetsController(ConsensusService consensusService, LeaderboardSyncService syncService) {
        this.consensusService = consensusService;
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
