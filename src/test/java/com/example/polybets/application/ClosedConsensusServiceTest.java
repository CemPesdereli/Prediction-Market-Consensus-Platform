package com.example.polybets.application;

import com.example.polybets.domain.model.ClosedConsensusMarket;
import com.example.polybets.domain.model.ClosedPosition;
import com.example.polybets.domain.model.Trader;
import com.example.polybets.domain.port.ConsensusRepositoryPort;
import com.example.polybets.domain.port.PositionsPort;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * calculate() saf bir fonksiyon olduğu için tüm senaryolar I/O olmadan test
 * edilebiliyor. repositoryPort/positionsPort sadece constructor'ı doldurmak
 * için mock'lanıyor, hiç çağrılmıyor.
 */
class ClosedConsensusServiceTest {

    private final ClosedConsensusService service = new ClosedConsensusService(
            Mockito.mock(ConsensusRepositoryPort.class),
            Mockito.mock(PositionsPort.class),
            3,
            3);

    private final Instant now = Instant.parse("2026-07-25T12:00:00Z");

    @Test
    void sadeceSonPencereIcindeKapananPozisyonlarDahilEdilmeli() {
        Trader a = new Trader("0xA", "traderA", 1, 500, 1000);
        Trader b = new Trader("0xB", "traderB", 2, 300, 1000);
        Trader c = new Trader("0xC", "traderC", 3, 100, 1000);

        ClosedPosition posA = lost("0xA", "market-1", "Yes", -50.0, -25.0, now.minus(1, ChronoUnit.DAYS).toString());
        ClosedPosition posB = lost("0xB", "market-1", "Yes", -20.0, -10.0, now.minus(2, ChronoUnit.DAYS).toString());
        ClosedPosition posC = lost("0xC", "market-1", "No", -10.0, -5.0, now.minus(3, ChronoUnit.DAYS).toString());
        // Pencere disinda (5 gun once kapanmis) -- dahil edilmemeli
        ClosedPosition tooOld = lost("0xA", "market-2", "Yes", -100.0, -50.0, now.minus(5, ChronoUnit.DAYS).toString());

        List<ClosedConsensusMarket> result = service.calculate(
                List.of(a, b, c), List.of(posA, posB, posC, tooOld), 3, 3, now);

        assertThat(result).hasSize(1);
        ClosedConsensusMarket market = result.get(0);
        assertThat(market.conditionId()).isEqualTo("market-1");
        assertThat(market.holderCount()).isEqualTo(3);
        assertThat(market.cohortSize()).isEqualTo(3);
    }

    @Test
    void minCommonHoldersEsiginiGecmeyenMarketlerDislanmali() {
        Trader a = new Trader("0xA", "traderA", 1, 500, 1000);
        Trader b = new Trader("0xB", "traderB", 2, 300, 1000);
        Trader c = new Trader("0xC", "traderC", 3, 100, 1000);

        // Sadece A ve B ayni markette -> esik 3'u gecmiyor
        ClosedPosition posA = lost("0xA", "market-1", "Yes", -50.0, -25.0, now.minus(1, ChronoUnit.DAYS).toString());
        ClosedPosition posB = lost("0xB", "market-1", "No", -20.0, -10.0, now.minus(1, ChronoUnit.DAYS).toString());

        List<ClosedConsensusMarket> result = service.calculate(
                List.of(a, b, c), List.of(posA, posB), 3, 3, now);

        assertThat(result).isEmpty();
    }

    @Test
    void holderDetaylariOutcomeVeKarZararIcermeli() {
        Trader a = new Trader("0xA", "traderA", 1, 500, 1000);
        Trader b = new Trader("0xB", "traderB", 2, 300, 1000);
        Trader c = new Trader("0xC", "traderC", 3, 100, 1000);

        ClosedPosition posA = lost("0xA", "market-1", "Yes", -120.0, -60.0, now.minus(1, ChronoUnit.DAYS).toString());
        ClosedPosition posB = lost("0xB", "market-1", "No", -30.0, -15.0, now.minus(1, ChronoUnit.DAYS).toString());
        ClosedPosition posC = lost("0xC", "market-1", "Yes", -5.0, -2.5, now.minus(1, ChronoUnit.DAYS).toString());

        List<ClosedConsensusMarket> result = service.calculate(
                List.of(a, b, c), List.of(posA, posB, posC), 3, 3, now);

        assertThat(result).hasSize(1);
        List<ClosedConsensusMarket.HolderOutcome> holders = result.get(0).holders();
        assertThat(holders).hasSize(3);
        assertThat(holders).allMatch(h -> !h.won());

        // En az kaybeden (C, -5) kaybedenler arasinda basta olmali (kayip buyuklugune gore sirali)
        assertThat(holders.get(0).proxyWallet()).isEqualTo("0xC");
        assertThat(holders.get(0).outcome()).isEqualTo("Yes");
        assertThat(holders.get(0).cashPnl()).isCloseTo(-5.0, within(0.001));
        assertThat(holders.get(0).percentPnl()).isCloseTo(-2.5, within(0.001));
        assertThat(holders.get(0).spentValue()).isCloseTo(5.0, within(0.001));

        ClosedConsensusMarket.HolderOutcome holderB = holders.stream()
                .filter(h -> h.proxyWallet().equals("0xB")).findFirst().orElseThrow();
        assertThat(holderB.outcome()).isEqualTo("No");
        assertThat(holderB.cashPnl()).isCloseTo(-30.0, within(0.001));
    }

    @Test
    void redeemKaynakliKazananWonTrueVeNetKariBilinmeyenOlarakDahilEdilir() {
        // A ve B: /positions?redeemable=true'dan gelen kaybedenler (net cashPnl biliniyor, won=false)
        // C: REDEEM aktivitesinden gelen kazanan -- won=true, cashPnl/percentPnl null (bilinmiyor)
        Trader a = new Trader("0xA", "traderA", 1, 500, 1000);
        Trader b = new Trader("0xB", "traderB", 2, 300, 1000);
        Trader c = new Trader("0xC", "traderC", 3, 100, 1000);

        ClosedPosition posA = lost("0xA", "market-1", "No", -30.0, -15.0, now.minus(1, ChronoUnit.DAYS).toString());
        ClosedPosition posB = lost("0xB", "market-1", "No", -10.0, -5.0, now.minus(1, ChronoUnit.DAYS).toString());
        ClosedPosition posC = won("0xC", "market-1", "Yes", now.minus(1, ChronoUnit.DAYS).toString());

        List<ClosedConsensusMarket> result = service.calculate(
                List.of(a, b, c), List.of(posA, posB, posC), 3, 3, now);

        assertThat(result).hasSize(1);
        ClosedConsensusMarket market = result.get(0);
        assertThat(market.holderCount()).isEqualTo(3);

        List<ClosedConsensusMarket.HolderOutcome> holders = market.holders();

        // Kazanan (C) won=true olmali, cashPnl/percentPnl null olarak korunmali -- uydurulmamali
        ClosedConsensusMarket.HolderOutcome holderC = holders.stream()
                .filter(h -> h.proxyWallet().equals("0xC")).findFirst().orElseThrow();
        assertThat(holderC.won()).isTrue();
        assertThat(holderC.outcome()).isEqualTo("Yes");
        assertThat(holderC.cashPnl()).isNull();
        assertThat(holderC.percentPnl()).isNull();

        // Kaybedenler won=false ve gercek cashPnl'e sahip olmali
        ClosedConsensusMarket.HolderOutcome holderA = holders.stream()
                .filter(h -> h.proxyWallet().equals("0xA")).findFirst().orElseThrow();
        assertThat(holderA.won()).isFalse();
        assertThat(holderA.cashPnl()).isCloseTo(-30.0, within(0.001));

        // Siralama: kazanan (won=true) once gelmeli
        assertThat(holders.get(0).proxyWallet()).isEqualTo("0xC");
    }

    @Test
    void holderWeightAktifConsensusIleAyniRoiFormulunuKullanmali() {
        // A: pnl=1000, vol=1000 -> roi=1.0 (en iyi) -> weight 3.0
        // B: pnl=100,  vol=1000 -> roi=0.1          -> weight 1.2
        // C: pnl=0,    vol=1000 -> roi=0.0 (en kotu) -> weight 1.0
        Trader a = new Trader("0xA", "traderA", 1, 1000, 1000);
        Trader b = new Trader("0xB", "traderB", 2, 100, 1000);
        Trader c = new Trader("0xC", "traderC", 3, 0, 1000);

        ClosedPosition posA = lost("0xA", "market-1", "Yes", -50.0, -25.0, now.minus(1, ChronoUnit.DAYS).toString());
        ClosedPosition posB = lost("0xB", "market-1", "No", -20.0, -10.0, now.minus(1, ChronoUnit.DAYS).toString());
        ClosedPosition posC = lost("0xC", "market-1", "Yes", -10.0, -5.0, now.minus(1, ChronoUnit.DAYS).toString());

        List<ClosedConsensusMarket> result = service.calculate(
                List.of(a, b, c), List.of(posA, posB, posC), 3, 3, now);

        assertThat(result).hasSize(1);
        List<ClosedConsensusMarket.HolderOutcome> holders = result.get(0).holders();

        double weightOfA = holders.stream().filter(h -> h.proxyWallet().equals("0xA")).findFirst().orElseThrow().weight();
        double weightOfB = holders.stream().filter(h -> h.proxyWallet().equals("0xB")).findFirst().orElseThrow().weight();
        double weightOfC = holders.stream().filter(h -> h.proxyWallet().equals("0xC")).findFirst().orElseThrow().weight();

        assertThat(weightOfA).isCloseTo(3.0, within(0.001));
        assertThat(weightOfB).isCloseTo(1.2, within(0.001));
        assertThat(weightOfC).isCloseTo(1.0, within(0.001));
    }

    @Test
    void sadeceTarihFormatindakiEndDateDeSonPencereFiltresindenGecmeli() {
        // /positions?redeemable=true endpoint'i endDate'i saat olmadan sadece
        // "yyyy-MM-dd" olarak dondugu icin bu format da dogru parse edilmeli --
        // aksi halde (regresyon) TUM kaybedenler sessizce elenir, sadece REDEEM
        // aktivitesinden gelen (tam ISO instant'li) kazananlar kalir.
        Trader a = new Trader("0xA", "traderA", 1, 500, 1000);
        Trader b = new Trader("0xB", "traderB", 2, 300, 1000);
        Trader c = new Trader("0xC", "traderC", 3, 100, 1000);

        // now = 2026-07-25T12:00:00Z -> pencere baslangici 2026-07-22T12:00:00Z
        // "2026-07-23" (gun basi UTC) bu pencerenin icinde olmali
        ClosedPosition posA = lost("0xA", "market-1", "Yes", -23.6, -99.9, "2026-07-23");
        ClosedPosition posB = lost("0xB", "market-1", "Yes", -12.8, -99.9, "2026-07-23");
        ClosedPosition posC = lost("0xC", "market-1", "Yes", -5.0, -50.0, "2026-07-23");

        List<ClosedConsensusMarket> result = service.calculate(
                List.of(a, b, c), List.of(posA, posB, posC), 3, 3, now);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).holders()).hasSize(3);
        assertThat(result.get(0).holders()).allMatch(h -> !h.won());
    }

    private ClosedPosition lost(
            String wallet, String conditionId, String outcome, Double cashPnl, Double percentPnl, String endDate) {
        // Kaybedenlerde currentValue genelde 0'a dustugu icin spentValue = -cashPnl
        // (API'nin initialValue alani gercek veride de bu sekilde davraniyor, bkz. CLAUDE.md).
        return new ClosedPosition(wallet, conditionId, "Test Market", "test-market", "test-event",
                outcome, false, cashPnl, percentPnl, -cashPnl, endDate);
    }

    private ClosedPosition won(String wallet, String conditionId, String outcome, String endDate) {
        return new ClosedPosition(wallet, conditionId, "Test Market", "test-market", "test-event",
                outcome, true, null, null, null, endDate);
    }
}
