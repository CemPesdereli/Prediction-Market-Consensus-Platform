package com.example.polybets.adapter.out.polymarket;

import com.example.polybets.domain.model.ClosedPosition;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * PolymarketPositionsAdapter'ın kazanan (redeem edilmiş) pozisyonlar için gerçek
 * net kâr hesaplamasını WireMock ile simüle edilmiş bir sunucuya karşı test eder.
 * Buradaki sayılar, gerçek bir top-20 cüzdanının (opopv., Taipei market) canlı işlem
 * geçmişiyle elle doğrulanmış aynı formülün (bkz. CLAUDE.md) küçültülmüş halidir.
 */
class PolymarketPositionsAdapterTest {

    private WireMockServer wireMockServer;
    private PolymarketPositionsAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build();

        adapter = new PolymarketPositionsAdapter(webClient, 500);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void satisOlmayanKazananPozisyonunNetKariDogruHesaplanmali() {
        stubRedeem("""
                [
                  { "proxyWallet": "0xAAA", "timestamp": 1700000000, "conditionId": "cond-1",
                    "type": "REDEEM", "size": 10.0, "usdcSize": 10.0,
                    "title": "Test Market", "slug": "test-market", "eventSlug": "test-event", "outcome": "Yes" }
                ]
                """);
        stubTrades("cond-1", """
                [
                  { "proxyWallet": "0xAAA", "timestamp": 1699000000, "conditionId": "cond-1",
                    "type": "TRADE", "size": 10.0, "usdcSize": 5.0, "price": 0.5, "side": "BUY",
                    "title": "Test Market", "slug": "test-market", "eventSlug": "test-event", "outcome": "Yes" }
                ]
                """);

        List<ClosedPosition> result = adapter.fetchRedeemedPositions("0xAAA", Instant.EPOCH);

        assertThat(result).hasSize(1);
        ClosedPosition p = result.get(0);
        assertThat(p.won()).isTrue();
        // maliyet 5.0 (10 hisse @ 0.5), redeem odemesi 10.0 -> net kar 5.0, %100 getiri
        assertThat(p.cashPnl()).isCloseTo(5.0, within(0.001));
        assertThat(p.percentPnl()).isCloseTo(100.0, within(0.001));
    }

    @Test
    void kismenSatilmisKazananPozisyonunNetKariSatislarDahilHesaplanmali() {
        stubRedeem("""
                [
                  { "proxyWallet": "0xAAA", "timestamp": 1700000000, "conditionId": "cond-2",
                    "type": "REDEEM", "size": 10.0, "usdcSize": 10.0,
                    "title": "Test Market 2", "slug": "test-market-2", "eventSlug": "test-event-2", "outcome": "Yes" }
                ]
                """);
        stubTrades("cond-2", """
                [
                  { "proxyWallet": "0xAAA", "timestamp": 1699000000, "conditionId": "cond-2",
                    "type": "TRADE", "size": 20.0, "usdcSize": 10.0, "price": 0.5, "side": "BUY",
                    "title": "Test Market 2", "slug": "test-market-2", "eventSlug": "test-event-2", "outcome": "Yes" },
                  { "proxyWallet": "0xAAA", "timestamp": 1699500000, "conditionId": "cond-2",
                    "type": "TRADE", "size": 10.0, "usdcSize": 7.0, "price": 0.7, "side": "SELL",
                    "title": "Test Market 2", "slug": "test-market-2", "eventSlug": "test-event-2", "outcome": "Yes" }
                ]
                """);

        List<ClosedPosition> result = adapter.fetchRedeemedPositions("0xAAA", Instant.EPOCH);

        assertThat(result).hasSize(1);
        ClosedPosition p = result.get(0);
        // maliyet 10.0 (20 hisse @ 0.5), satistan 7.0 + redeem'den 10.0 = 17.0 toplam getiri
        // -> net kar 7.0, maliyete gore %70 getiri
        assertThat(p.cashPnl()).isCloseTo(7.0, within(0.001));
        assertThat(p.percentPnl()).isCloseTo(70.0, within(0.001));
    }

    @Test
    void islemGecmisiHisseSayisiylaUyusmuyorsaNetKarNullBirakilmali() {
        stubRedeem("""
                [
                  { "proxyWallet": "0xAAA", "timestamp": 1700000000, "conditionId": "cond-3",
                    "type": "REDEEM", "size": 10.0, "usdcSize": 10.0,
                    "title": "Test Market 3", "slug": "test-market-3", "eventSlug": "test-event-3", "outcome": "Yes" }
                ]
                """);
        // Sadece 3 hisselik alim donuyor -- redeem'deki 10 hisseyle uyusmuyor
        // (ornegin positionsLimit asilip eski islemler eksik geldiginde olabilecek durum).
        stubTrades("cond-3", """
                [
                  { "proxyWallet": "0xAAA", "timestamp": 1699000000, "conditionId": "cond-3",
                    "type": "TRADE", "size": 3.0, "usdcSize": 1.5, "price": 0.5, "side": "BUY",
                    "title": "Test Market 3", "slug": "test-market-3", "eventSlug": "test-event-3", "outcome": "Yes" }
                ]
                """);

        List<ClosedPosition> result = adapter.fetchRedeemedPositions("0xAAA", Instant.EPOCH);

        assertThat(result).hasSize(1);
        ClosedPosition p = result.get(0);
        assertThat(p.won()).isTrue();
        assertThat(p.cashPnl()).isNull();
        assertThat(p.percentPnl()).isNull();
    }

    private void stubRedeem(String body) {
        wireMockServer.stubFor(get(urlPathEqualTo("/activity"))
                .withQueryParam("type", equalTo("REDEEM"))
                .willReturn(okJson(body)));
    }

    private void stubTrades(String conditionId, String body) {
        wireMockServer.stubFor(get(urlPathEqualTo("/activity"))
                .withQueryParam("type", equalTo("TRADE"))
                .withQueryParam("market", equalTo(conditionId))
                .willReturn(okJson(body)));
    }
}
