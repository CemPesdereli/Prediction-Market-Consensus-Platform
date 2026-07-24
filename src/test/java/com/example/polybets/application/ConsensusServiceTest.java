package com.example.polybets.application;

import com.example.polybets.domain.model.ActivePosition;
import com.example.polybets.domain.model.ConsensusMarket;
import com.example.polybets.domain.model.Trader;
import com.example.polybets.domain.port.ConsensusRepositoryPort;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * calculate() saf bir fonksiyon olduğu için tüm senaryolar I/O olmadan,
 * doğrudan hazırlanmış Trader/ActivePosition listeleriyle test edilebiliyor.
 * repositoryPort sadece constructor'ı doldurmak için mock'lanıyor, hiç çağrılmıyor.
 */
class ConsensusServiceTest {

    private final ConsensusService service =
            new ConsensusService(Mockito.mock(ConsensusRepositoryPort.class), 2);

    @Test
    void enIyiRoiliTraderEnYuksekAgirligiAlmali() {
        // A: pnl=1000, vol=1000 -> roi=1.0 (en iyi)
        // B: pnl=100,  vol=1000 -> roi=0.1
        // C: pnl=0,    vol=1000 -> roi=0.0 (en kotu)
        Trader a = new Trader("0xA", "traderA", 1, 1000, 1000);
        Trader b = new Trader("0xB", "traderB", 2, 100, 1000);
        Trader c = new Trader("0xC", "traderC", 3, 0, 1000);

        ActivePosition posA = position("0xA", "market-1", "Yes");
        ActivePosition posB = position("0xB", "market-1", "Yes");
        ActivePosition posC = position("0xC", "market-1", "No");

        List<ConsensusMarket> result = service.calculate(
                List.of(a, b, c), List.of(posA, posB, posC), 2);

        assertThat(result).hasSize(1);
        ConsensusMarket market = result.get(0);

        // Butun kohort (3 kisi) ayni markette -> weightedConsensusPercent %100 olmali
        assertThat(market.weightedConsensusPercent()).isCloseTo(100.0, within(0.001));
        assertThat(market.holderCount()).isEqualTo(3);
        assertThat(market.cohortSize()).isEqualTo(3);

        // A en iyi ROI'li oldugu icin holder listesinde en yuksek agirliga sahip olmali
        double weightOfA = market.holders().stream()
                .filter(h -> h.proxyWallet().equals("0xA"))
                .findFirst().orElseThrow().weight();
        double weightOfC = market.holders().stream()
                .filter(h -> h.proxyWallet().equals("0xC"))
                .findFirst().orElseThrow().weight();

        assertThat(weightOfA).isCloseTo(3.0, within(0.001)); // en iyi ROI -> ust sinir
        assertThat(weightOfC).isCloseTo(1.0, within(0.001)); // en kotu ROI -> alt sinir (0 degil)
        assertThat(weightOfA).isGreaterThan(weightOfC);
    }

    @Test
    void minCommonHoldersEsiginiGecmeyenMarketlerDislanmali() {
        Trader a = new Trader("0xA", "traderA", 1, 500, 1000);
        Trader b = new Trader("0xB", "traderB", 2, 500, 1000);

        // Sadece A bu markette pozisyon tutuyor -> min 2 esigini gecmiyor
        ActivePosition onlyA = position("0xA", "market-lonely", "Yes");

        List<ConsensusMarket> result = service.calculate(List.of(a, b), List.of(onlyA), 2);

        assertThat(result).isEmpty();
    }

    @Test
    void sentimentYesPercentDogruHesaplanmali() {
        Trader a = new Trader("0xA", "traderA", 1, 500, 1000); // roi=0.5
        Trader b = new Trader("0xB", "traderB", 2, 500, 1000); // roi=0.5 (esit -> normalized=0.5, weight=2.0)

        ActivePosition yesA = position("0xA", "market-1", "Yes");
        ActivePosition noB = position("0xB", "market-1", "No");

        List<ConsensusMarket> result = service.calculate(List.of(a, b), List.of(yesA, noB), 2);

        assertThat(result).hasSize(1);
        // Esit ROI -> esit agirlik (2.0 & 2.0) -> %50 Yes, %50 No
        assertThat(result.get(0).sentimentYesPercent()).isCloseTo(50.0, within(0.001));
    }

    private ActivePosition position(String wallet, String conditionId, String outcome) {
        return new ActivePosition(wallet, conditionId, "Test Market", "test-market", "test-event",
                outcome, 0.5, 100.0, "2026-12-31");
    }
}
