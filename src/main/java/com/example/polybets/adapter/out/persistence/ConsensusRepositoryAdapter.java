package com.example.polybets.adapter.out.persistence;

import com.example.polybets.adapter.out.persistence.entity.LeaderboardEntryEntity;
import com.example.polybets.adapter.out.persistence.entity.PositionSnapshotEntity;
import com.example.polybets.domain.model.ActivePosition;
import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.Trader;
import com.example.polybets.domain.port.ConsensusRepositoryPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ConsensusRepositoryPort'un PostgreSQL/JPA implementasyonu. Domain katmanı bu
 * sınıfın varlığından habersizdir; sadece port interface'ini bilir.
 */
@Component
public class ConsensusRepositoryAdapter implements ConsensusRepositoryPort {

    private final JpaLeaderboardEntryRepository leaderboardRepository;
    private final JpaPositionSnapshotRepository positionRepository;

    public ConsensusRepositoryAdapter(
            JpaLeaderboardEntryRepository leaderboardRepository,
            JpaPositionSnapshotRepository positionRepository) {
        this.leaderboardRepository = leaderboardRepository;
        this.positionRepository = positionRepository;
    }

    @Override
    @Transactional
    public void saveSnapshot(Category category, List<Trader> traders, List<ActivePosition> positions, Instant syncedAt) {
        leaderboardRepository.deleteByCategory(category);
        positionRepository.deleteByCategory(category);

        List<LeaderboardEntryEntity> entryEntities = traders.stream()
                .map(t -> new LeaderboardEntryEntity(
                        category, t.rank(), t.proxyWallet(), t.userName(), t.vol(), t.pnl(), syncedAt))
                .collect(Collectors.toList());
        leaderboardRepository.saveAll(entryEntities);

        List<PositionSnapshotEntity> positionEntities = positions.stream()
                .map(p -> new PositionSnapshotEntity(
                        category, p.proxyWallet(), userNameFor(traders, p.proxyWallet()), p.conditionId(),
                        p.marketTitle(), p.marketSlug(), p.eventSlug(), p.outcome(),
                        p.curPrice(), p.avgPrice(), p.currentValue(), p.initialValue(), p.endDate(), syncedAt))
                .collect(Collectors.toList());
        positionRepository.saveAll(positionEntities);
    }

    @Override
    public List<Trader> findLatestTraders(Category category) {
        return leaderboardRepository.findByCategoryOrderByRankAsc(category).stream()
                .map(e -> new Trader(e.getProxyWallet(), e.getUserName(), e.getRank(), e.getPnl(), e.getVol()))
                .collect(Collectors.toList());
    }

    @Override
    public List<ActivePosition> findLatestPositions(Category category) {
        return positionRepository.findByCategory(category).stream()
                .map(e -> new ActivePosition(
                        e.getProxyWallet(), e.getConditionId(), e.getMarketTitle(), e.getMarketSlug(),
                        e.getEventSlug(), e.getOutcome(), e.getCurPrice(), e.getAvgPrice(), e.getCurrentValue(),
                        e.getInitialValue(), e.getEndDate()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Instant> findLastSyncedAt(Category category) {
        return leaderboardRepository.findByCategoryOrderByRankAsc(category).stream()
                .map(LeaderboardEntryEntity::getSyncedAt)
                .max(Comparator.naturalOrder());
    }

    private String userNameFor(List<Trader> traders, String proxyWallet) {
        return traders.stream()
                .filter(t -> t.proxyWallet().equals(proxyWallet))
                .map(Trader::userName)
                .findFirst()
                .orElse(null);
    }
}
