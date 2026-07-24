package com.example.polybets.adapter.out.polymarket;

import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.Trader;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PolymarketLeaderboardAdapter'ı gerçek Polymarket sunucusuna gitmeden,
 * WireMock ile simüle edilmiş bir HTTP sunucusuna karşı test eder.
 * Böylece API şeması değişirse (ya da response'da beklenmedik alan olursa)
 * bunu gerçek bir ağ çağrısı yapmadan, hızlı ve deterministik şekilde yakalarız.
 */
class PolymarketLeaderboardAdapterTest {

    private WireMockServer wireMockServer;
    private PolymarketLeaderboardAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build();

        adapter = new PolymarketLeaderboardAdapter(webClient, "MONTH", "PNL");
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void leaderboardYanitiDogruSekildeDomaineCevrilmeli() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/leaderboard"))
                .withQueryParam("category", equalTo("WEATHER"))
                .withQueryParam("timePeriod", equalTo("MONTH"))
                .willReturn(okJson("""
                        [
                          {
                            "rank": "1",
                            "proxyWallet": "0xAAA",
                            "userName": "weatherWizard",
                            "vol": 100000.0,
                            "pnl": 25000.0,
                            "verifiedBadge": true
                          },
                          {
                            "rank": "2",
                            "proxyWallet": "0xBBB",
                            "userName": "rainTrader",
                            "vol": 50000.0,
                            "pnl": 5000.0,
                            "verifiedBadge": false
                          }
                        ]
                        """)));

        List<Trader> traders = adapter.fetchMonthlyLeaderboard(Category.WEATHER, 20);

        assertThat(traders).hasSize(2);
        assertThat(traders.get(0).proxyWallet()).isEqualTo("0xAAA");
        assertThat(traders.get(0).userName()).isEqualTo("weatherWizard");
        assertThat(traders.get(0).pnl()).isEqualTo(25000.0);
        assertThat(traders.get(0).vol()).isEqualTo(100000.0);
        assertThat(traders.get(0).rank()).isEqualTo(1);
        assertThat(traders.get(1).rank()).isEqualTo(2);
    }

    @Test
    void sunucuHataDonerseBosListeIleGeriDonulmeli() {
        wireMockServer.stubFor(get(urlPathEqualTo("/v1/leaderboard"))
                .willReturn(serverError()));

        List<Trader> traders = adapter.fetchMonthlyLeaderboard(Category.WEATHER, 20);

        assertThat(traders).isEmpty();
    }
}
