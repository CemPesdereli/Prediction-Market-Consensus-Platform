package com.example.polybets.adapter.out.persistence;

import com.example.polybets.domain.model.ActivePosition;
import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.Trader;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ConsensusRepositoryAdapter'ı gerçek bir PostgreSQL container'ına karşı test eder.
 * Böylece Flyway migration'ının, JPA mapping'lerinin ve sorgu mantığının gerçek
 * bir veritabanı motoruna karşı çalıştığını (H2 gibi bir "sahte" DB ile değil)
 * doğrulamış oluruz.
 */
@SpringBootTest
@Testcontainers
class ConsensusRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("polybets_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Test context ayaga kalkarken gercek Polymarket API'sine istek atilmasin diye
        // @PostConstruct senkronizasyonunu devre disi birakiyoruz.
        registry.add("polymarket.sync.enabled", () -> "false");
    }

    @Autowired
    private ConsensusRepositoryAdapter repositoryAdapter;

    @Test
    void snapshotKaydedipGeriOkuyabilmeli() {
        Trader trader1 = new Trader("0xAAA", "weatherWizard", 1, 25000.0, 100000.0);
        Trader trader2 = new Trader("0xBBB", "rainTrader", 2, 5000.0, 50000.0);

        ActivePosition position1 = new ActivePosition(
                "0xAAA", "cond-1", "NY Rain", "ny-rain", "ny-weather-event",
                "Yes", 0.65, 0.55, 1300.0, "2026-12-31");
        ActivePosition position2 = new ActivePosition(
                "0xBBB", "cond-1", "NY Rain", "ny-rain", "ny-weather-event",
                "No", 0.35, 0.30, 400.0, "2026-12-31");

        Instant syncedAt = Instant.now();
        repositoryAdapter.saveSnapshot(Category.WEATHER, List.of(trader1, trader2),
                List.of(position1, position2), syncedAt);

        List<Trader> savedTraders = repositoryAdapter.findLatestTraders(Category.WEATHER);
        List<ActivePosition> savedPositions = repositoryAdapter.findLatestPositions(Category.WEATHER);
        Optional<Instant> lastSync = repositoryAdapter.findLastSyncedAt(Category.WEATHER);

        assertThat(savedTraders).hasSize(2);
        assertThat(savedTraders.get(0).proxyWallet()).isEqualTo("0xAAA");
        assertThat(savedTraders.get(0).rank()).isEqualTo(1);

        assertThat(savedPositions).hasSize(2);
        assertThat(savedPositions).extracting(ActivePosition::conditionId)
                .containsOnly("cond-1");

        assertThat(lastSync).isPresent();
    }

    @Test
    void yeniSenkronizasyonOncekiKaydinYerineGecmeli() {
        Trader trader = new Trader("0xCCC", "someTrader", 1, 1000.0, 10000.0);
        ActivePosition position = new ActivePosition(
                "0xCCC", "cond-old", "Old Market", "old-market", "old-event",
                "Yes", 0.5, 0.45, 100.0, "2026-01-01");

        repositoryAdapter.saveSnapshot(Category.SPORTS, List.of(trader), List.of(position), Instant.now());

        Trader newTrader = new Trader("0xDDD", "newTrader", 1, 2000.0, 20000.0);
        repositoryAdapter.saveSnapshot(Category.SPORTS, List.of(newTrader), List.of(), Instant.now());

        List<Trader> traders = repositoryAdapter.findLatestTraders(Category.SPORTS);
        assertThat(traders).hasSize(1);
        assertThat(traders.get(0).proxyWallet()).isEqualTo("0xDDD");
    }
}
