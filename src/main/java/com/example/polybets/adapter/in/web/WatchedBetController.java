package com.example.polybets.adapter.in.web;

import com.example.polybets.application.WatchedBetService;
import com.example.polybets.domain.model.WatchedBet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/watched-bets")
public class WatchedBetController {

    private final WatchedBetService watchedBetService;

    public WatchedBetController(WatchedBetService watchedBetService) {
        this.watchedBetService = watchedBetService;
    }

    /**
     * GET /api/watched-bets
     * Kullanicinin kurdugu tum fiyat alarmlari (aktif/tetiklenmis/iptal), en yeni once.
     */
    @GetMapping
    public ResponseEntity<List<WatchedBet>> list() {
        List<WatchedBet> all = watchedBetService.listAll();
        all.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return ResponseEntity.ok(all);
    }

    /**
     * POST /api/watched-bets
     * Yeni bir fiyat alarmi kurar. marketSlug Polymarket URL'sinin son parcasi
     * (ornegin polymarket.com/event/x/will-y adresindeki "will-y"), Gamma API
     * uzerinden conditionId'ye cozumlenir. entryPrice/targetPrice 0-1 arasi
     * ondalik (cent karsiligi = deger * 100).
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateWatchedBetRequest request) {
        try {
            WatchedBet created = watchedBetService.create(
                    request.marketSlug(), request.outcome(), request.entryPrice(), request.targetPrice());
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * DELETE /api/watched-bets/{id}
     * Alarmi iptal eder (kaydi silmez, status=CANCELLED yapar).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancel(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(watchedBetService.cancel(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * POST /api/watched-bets/check
     * 15 dakikalik zamanlanmis job'u beklemeden tum aktif alarmlari hemen
     * kontrol eder (demo/test icin -- ornegin Telegram bildiriminin gercekten
     * calistigini dogrulamak icin kullanilabilir).
     */
    @PostMapping("/check")
    public ResponseEntity<String> triggerCheck() {
        watchedBetService.checkPriceAlerts();
        return ResponseEntity.ok("Fiyat alarmlari kontrol edildi.");
    }

    public record CreateWatchedBetRequest(
            @NotBlank String marketSlug,
            @NotBlank String outcome,
            double entryPrice,
            double targetPrice
    ) {
    }
}
