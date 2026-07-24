package com.example.polybets.domain.port;

import com.example.polybets.domain.model.ActivePosition;
import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.Trader;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Bir kategorinin en son senkronize edilmiş leaderboard + pozisyon verisini
 * saklayan/okuyan port. Bugün PostgreSQL (JPA) tarafından implemente ediliyor.
 */
public interface ConsensusRepositoryPort {

    /**
     * Bir kategori için önceki kaydı temizleyip yeni senkronizasyon sonucunu yazar.
     */
    void saveSnapshot(Category category, List<Trader> traders, List<ActivePosition> positions, Instant syncedAt);

    List<Trader> findLatestTraders(Category category);

    List<ActivePosition> findLatestPositions(Category category);

    Optional<Instant> findLastSyncedAt(Category category);
}
