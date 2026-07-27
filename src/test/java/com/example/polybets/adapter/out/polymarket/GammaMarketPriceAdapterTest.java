package com.example.polybets.adapter.out.polymarket;

import com.example.polybets.domain.model.MarketSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Gamma API'nin outcomes/outcomePrices alanlarini JSON-encode edilmis STRING
 * olarak dondurmesi (duz dizi degil) bilinen bir tuhaflik -- bu test adapter'in
 * bunu dogru parse ettigini ve eksik taraf icin tumleyen fiyati (1 - diger)
 * dogru turettigini dogruluyor.
 */
class GammaMarketPriceAdapterTest {

    private WireMockServer wireMockServer;
    private GammaMarketPriceAdapter adapter;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://localhost:" + wireMockServer.port())
                .build();

        // Gercek uygulamada gamma ve clob farkli host'lar ama testte ayni
        // WireMock sunucusu ikisini de karsiliyor (path'ler zaten farkli: /markets vs /midpoint).
        adapter = new GammaMarketPriceAdapter(webClient, webClient, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void slugtanMarketCozumlenirVeFiyatlarParseEdilir() {
        wireMockServer.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("slug", equalTo("will-x-happen"))
                .willReturn(okJson("""
                        [
                          { "conditionId": "cond-1", "question": "Will X happen?", "slug": "will-x-happen",
                            "outcomes": "[\\"Yes\\", \\"No\\"]", "outcomePrices": "[\\"0.965\\", \\"0.035\\"]",
                            "endDate": "2026-12-31",
                            "events": [ { "slug": "event-x" } ] }
                        ]
                        """)));

        Optional<MarketSnapshot> result = adapter.fetchMarketBySlug("will-x-happen");

        assertThat(result).isPresent();
        MarketSnapshot snapshot = result.get();
        assertThat(snapshot.conditionId()).isEqualTo("cond-1");
        assertThat(snapshot.eventSlug()).isEqualTo("event-x");
        assertThat(snapshot.yesPrice()).isCloseTo(0.965, within(0.0001));
        assertThat(snapshot.noPrice()).isCloseTo(0.035, within(0.0001));
    }

    @Test
    void sadeceBirTarafinFiyatiVarsaTumleyenTuretilir() {
        wireMockServer.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("condition_ids", equalTo("cond-2"))
                .willReturn(okJson("""
                        [
                          { "conditionId": "cond-2", "question": "Will Y happen?", "slug": "will-y-happen",
                            "outcomes": "[\\"Yes\\"]", "outcomePrices": "[\\"0.42\\"]",
                            "endDate": "2026-12-31", "events": [] }
                        ]
                        """)));

        Optional<MarketSnapshot> result = adapter.fetchMarketByConditionId("cond-2");

        assertThat(result).isPresent();
        assertThat(result.get().yesPrice()).isCloseTo(0.42, within(0.0001));
        assertThat(result.get().noPrice()).isCloseTo(0.58, within(0.0001));
        assertThat(result.get().eventSlug()).isNull();
    }

    @Test
    void clobMidpointVarsaGammaninOutcomePricesiniEzer() {
        // Canli veride dogrulandi: negRisk (weather bucket) marketlerde Gamma'nin
        // outcomePrices'i bayat olabiliyor (bkz. GammaMarketPriceAdapter dokumani) --
        // "highest-temperature-in-london-on-july-27-2026-26c" marketinde Gamma
        // Yes=0.395 derken CLOB /midpoint ayni anda Yes=0.575 veriyordu.
        wireMockServer.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("slug", equalTo("will-z-happen"))
                .willReturn(okJson("""
                        [
                          { "conditionId": "cond-3", "question": "Will Z happen?", "slug": "will-z-happen",
                            "outcomes": "[\\"Yes\\", \\"No\\"]", "outcomePrices": "[\\"0.395\\", \\"0.605\\"]",
                            "clobTokenIds": "[\\"yes-token\\", \\"no-token\\"]",
                            "endDate": "2026-12-31", "events": [] }
                        ]
                        """)));
        wireMockServer.stubFor(get(urlPathEqualTo("/midpoint"))
                .withQueryParam("token_id", equalTo("yes-token"))
                .willReturn(okJson("{ \"mid\": \"0.575\" }")));
        wireMockServer.stubFor(get(urlPathEqualTo("/midpoint"))
                .withQueryParam("token_id", equalTo("no-token"))
                .willReturn(okJson("{ \"mid\": \"0.425\" }")));

        Optional<MarketSnapshot> result = adapter.fetchMarketBySlug("will-z-happen");

        assertThat(result).isPresent();
        assertThat(result.get().yesPrice()).isCloseTo(0.575, within(0.0001));
        assertThat(result.get().noPrice()).isCloseTo(0.425, within(0.0001));
    }

    @Test
    void clobMidpointBasarisizOlursaGammaFiyatinaGeriDusulur() {
        wireMockServer.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("slug", equalTo("will-w-happen"))
                .willReturn(okJson("""
                        [
                          { "conditionId": "cond-4", "question": "Will W happen?", "slug": "will-w-happen",
                            "outcomes": "[\\"Yes\\", \\"No\\"]", "outcomePrices": "[\\"0.30\\", \\"0.70\\"]",
                            "clobTokenIds": "[\\"yes-token-2\\", \\"no-token-2\\"]",
                            "endDate": "2026-12-31", "events": [] }
                        ]
                        """)));
        wireMockServer.stubFor(get(urlPathEqualTo("/midpoint"))
                .withQueryParam("token_id", equalTo("yes-token-2"))
                .willReturn(aResponse().withStatus(500)));
        wireMockServer.stubFor(get(urlPathEqualTo("/midpoint"))
                .withQueryParam("token_id", equalTo("no-token-2"))
                .willReturn(aResponse().withStatus(500)));

        Optional<MarketSnapshot> result = adapter.fetchMarketBySlug("will-w-happen");

        assertThat(result).isPresent();
        assertThat(result.get().yesPrice()).isCloseTo(0.30, within(0.0001));
        assertThat(result.get().noPrice()).isCloseTo(0.70, within(0.0001));
    }

    @Test
    void marketBulunamazsaBosDoner() {
        wireMockServer.stubFor(get(urlPathEqualTo("/markets"))
                .withQueryParam("slug", equalTo("bilinmeyen-market"))
                .willReturn(okJson("[]")));

        Optional<MarketSnapshot> result = adapter.fetchMarketBySlug("bilinmeyen-market");

        assertThat(result).isEmpty();
    }
}
