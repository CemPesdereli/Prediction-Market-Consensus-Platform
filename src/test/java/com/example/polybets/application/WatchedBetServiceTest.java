package com.example.polybets.application;

import com.example.polybets.domain.model.MarketSnapshot;
import com.example.polybets.domain.model.WatchedBet;
import com.example.polybets.domain.model.WatchedBetStatus;
import com.example.polybets.domain.port.MarketPricePort;
import com.example.polybets.domain.port.NotificationPort;
import com.example.polybets.domain.port.WatchedBetRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * WatchedBetService'in create/cancel akislarini ve en kritik parca olan
 * checkPriceAlerts()'un yon-farkinda (kar-al / zarar-kes) tetikleme
 * mantigini test eder. Butun portlar mock -- I/O yok.
 */
class WatchedBetServiceTest {

    private final WatchedBetRepositoryPort repositoryPort = mock(WatchedBetRepositoryPort.class);
    private final MarketPricePort marketPricePort = mock(MarketPricePort.class);
    private final NotificationPort notificationPort = mock(NotificationPort.class);
    private final WatchedBetService service =
            new WatchedBetService(repositoryPort, marketPricePort, notificationPort, true);

    @Test
    void createMarketiSlugtanCozumleyipAktifAlarmKaydeder() {
        MarketSnapshot snapshot = new MarketSnapshot(
                "cond-1", "Will X happen?", "will-x-happen", "event-x", "2026-12-31", 0.40, 0.60);
        when(marketPricePort.fetchMarketBySlug("will-x-happen")).thenReturn(Optional.of(snapshot));
        when(repositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WatchedBet created = service.create("will-x-happen", "yes", 0.40, 0.70);

        assertThat(created.conditionId()).isEqualTo("cond-1");
        assertThat(created.outcome()).isEqualTo("Yes");
        assertThat(created.status()).isEqualTo(WatchedBetStatus.ACTIVE);
        assertThat(created.entryPrice()).isEqualTo(0.40);
        assertThat(created.targetPrice()).isEqualTo(0.70);
    }

    @Test
    void gecersizOutcomeIllegalArgumentFirlatir() {
        assertThatThrownBy(() -> service.create("slug", "maybe", 0.40, 0.70))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(marketPricePort, repositoryPort);
    }

    @Test
    void araligiDisindaFiyatIllegalArgumentFirlatir() {
        assertThatThrownBy(() -> service.create("slug", "yes", 0.0, 0.70))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("slug", "yes", 0.40, 1.5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hedefGirisinUstundeyseFiyatHedefeUlasinceTetiklenir() {
        // Kar-al senaryosu: giris 0.40, hedef 0.70 -> fiyat >= 0.70 olunca tetiklenmeli
        WatchedBet bet = activeBet(0.40, 0.70);
        when(repositoryPort.findByStatus(WatchedBetStatus.ACTIVE)).thenReturn(List.of(bet));
        when(marketPricePort.fetchMarketByConditionId("cond-1"))
                .thenReturn(Optional.of(snapshotWithPrice(0.72)));
        when(repositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.checkPriceAlerts();

        ArgumentCaptor<WatchedBet> savedCaptor = ArgumentCaptor.forClass(WatchedBet.class);
        verify(repositoryPort).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().status()).isEqualTo(WatchedBetStatus.TRIGGERED);
        verify(notificationPort).sendPriceAlert(any(), eq(0.72));
    }

    @Test
    void hedefGirisinAltindaysaFiyatHedefeDusunceTetiklenir() {
        // Zarar-kes senaryosu: giris 0.60, hedef 0.30 -> fiyat <= 0.30 olunca tetiklenmeli
        WatchedBet bet = activeBet(0.60, 0.30);
        when(repositoryPort.findByStatus(WatchedBetStatus.ACTIVE)).thenReturn(List.of(bet));
        when(marketPricePort.fetchMarketByConditionId("cond-1"))
                .thenReturn(Optional.of(snapshotWithPrice(0.25)));
        when(repositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.checkPriceAlerts();

        verify(notificationPort).sendPriceAlert(any(), eq(0.25));
    }

    @Test
    void hedefeUlasilmadiysaBildirimGitmezSadeceGuncellenir() {
        WatchedBet bet = activeBet(0.40, 0.70);
        when(repositoryPort.findByStatus(WatchedBetStatus.ACTIVE)).thenReturn(List.of(bet));
        when(marketPricePort.fetchMarketByConditionId("cond-1"))
                .thenReturn(Optional.of(snapshotWithPrice(0.55)));
        when(repositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.checkPriceAlerts();

        verifyNoInteractions(notificationPort);
        ArgumentCaptor<WatchedBet> savedCaptor = ArgumentCaptor.forClass(WatchedBet.class);
        verify(repositoryPort).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().status()).isEqualTo(WatchedBetStatus.ACTIVE);
        assertThat(savedCaptor.getValue().lastCheckedPrice()).isEqualTo(0.55);
    }

    @Test
    void fiyatAlinamayanMarketAtlanirKaydedilmez() {
        WatchedBet bet = activeBet(0.40, 0.70);
        when(repositoryPort.findByStatus(WatchedBetStatus.ACTIVE)).thenReturn(List.of(bet));
        when(marketPricePort.fetchMarketByConditionId("cond-1")).thenReturn(Optional.empty());

        service.checkPriceAlerts();

        verifyNoInteractions(notificationPort);
        verify(repositoryPort, Mockito.never()).save(any());
    }

    @Test
    void cancelDurumuCancelledYapar() {
        WatchedBet bet = activeBet(0.40, 0.70);
        when(repositoryPort.findById(1L)).thenReturn(Optional.of(bet));
        when(repositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WatchedBet cancelled = service.cancel(1L);

        assertThat(cancelled.status()).isEqualTo(WatchedBetStatus.CANCELLED);
    }

    @Test
    void olmayanAlarmiIptalEtmekNoSuchElementFirlatir() {
        when(repositoryPort.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(99L)).isInstanceOf(NoSuchElementException.class);
    }

    private WatchedBet activeBet(double entryPrice, double targetPrice) {
        return new WatchedBet(1L, "cond-1", "Test Market", "test-market", "test-event", "Yes",
                entryPrice, targetPrice, WatchedBetStatus.ACTIVE, Instant.now(), null, entryPrice, Instant.now());
    }

    private MarketSnapshot snapshotWithPrice(double yesPrice) {
        return new MarketSnapshot("cond-1", "Test Market", "test-market", "test-event", "2026-12-31",
                yesPrice, 1.0 - yesPrice);
    }
}
