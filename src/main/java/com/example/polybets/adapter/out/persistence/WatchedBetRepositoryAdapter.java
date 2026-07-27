package com.example.polybets.adapter.out.persistence;

import com.example.polybets.adapter.out.persistence.entity.WatchedBetEntity;
import com.example.polybets.domain.model.WatchedBet;
import com.example.polybets.domain.model.WatchedBetStatus;
import com.example.polybets.domain.port.WatchedBetRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * WatchedBetRepositoryPort'un PostgreSQL/JPA implementasyonu.
 */
@Component
public class WatchedBetRepositoryAdapter implements WatchedBetRepositoryPort {

    private final JpaWatchedBetRepository jpaRepository;

    public WatchedBetRepositoryAdapter(JpaWatchedBetRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public WatchedBet save(WatchedBet watchedBet) {
        WatchedBetEntity entity = jpaRepository.save(toEntity(watchedBet));
        return toDomain(entity);
    }

    @Override
    public List<WatchedBet> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<WatchedBet> findByStatus(WatchedBetStatus status) {
        return jpaRepository.findByStatus(status).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<WatchedBet> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private WatchedBetEntity toEntity(WatchedBet w) {
        return new WatchedBetEntity(
                w.id(), w.conditionId(), w.marketTitle(), w.marketSlug(), w.eventSlug(), w.outcome(),
                w.entryPrice(), w.targetPrice(), w.status(), w.createdAt(), w.triggeredAt(),
                w.lastCheckedPrice(), w.lastCheckedAt());
    }

    private WatchedBet toDomain(WatchedBetEntity e) {
        return new WatchedBet(
                e.getId(), e.getConditionId(), e.getMarketTitle(), e.getMarketSlug(), e.getEventSlug(),
                e.getOutcome(), e.getEntryPrice(), e.getTargetPrice(), e.getStatus(), e.getCreatedAt(),
                e.getTriggeredAt(), e.getLastCheckedPrice(), e.getLastCheckedAt());
    }
}
