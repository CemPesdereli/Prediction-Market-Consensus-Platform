package com.example.polybets.adapter.out.polymarket;

import com.example.polybets.adapter.out.polymarket.dto.ClobMidpointDto;
import com.example.polybets.adapter.out.polymarket.dto.GammaMarketDto;
import com.example.polybets.domain.model.MarketSnapshot;
import com.example.polybets.domain.port.MarketPricePort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MarketPricePort'un Polymarket Gamma + CLOB API implementasyonu.
 * GET https://gamma-api.polymarket.com/markets?slug=... veya ?condition_ids=...
 * Bir kullanıcı cüzdanına bağlı değildir -- WatchedBet özelliği için gerekli,
 * çünkü izlenen market kullanıcının kendi girdiği herhangi bir market olabilir
 * (top-20 consensus kümesinde olması gerekmez).
 *
 * ÖNEMLİ (canlı veride doğrulandı): Gamma'nın {@code outcomePrices} alanı
 * negRisk (gruplu bucket) marketlerde -- weather kategorisinde çok yaygın --
 * ciddi şekilde bayat/yanlış çıkabiliyor. Gerçek bir market üzerinde test
 * edildi ("highest-temperature-in-london-on-july-27-2026-26c"): Gamma
 * outcomePrices Yes=0.395 derken, aynı anda CLOB {@code /midpoint} Yes=0.575
 * veriyordu (siteden görünen gerçek fiyat da Gamma'dan ciddi şekilde
 * uzaktı). Bu yüzden gerçek fiyat CLOB {@code /midpoint} endpoint'inden
 * ({@code clobTokenIds} ile) çekiliyor; CLOB çağrısı başarısız olursa (ör.
 * orderbook kapalıysa) Gamma'nın outcomePrices'ı fallback olarak kullanılıyor.
 */
@Component
public class GammaMarketPriceAdapter implements MarketPricePort {

    private static final Logger log = LoggerFactory.getLogger(GammaMarketPriceAdapter.class);

    private final WebClient gammaWebClient;
    private final WebClient clobWebClient;
    private final ObjectMapper objectMapper;

    public GammaMarketPriceAdapter(WebClient gammaWebClient, WebClient clobWebClient, ObjectMapper objectMapper) {
        this.gammaWebClient = gammaWebClient;
        this.clobWebClient = clobWebClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<MarketSnapshot> fetchMarketBySlug(String slug) {
        return fetchFirst("slug", slug);
    }

    @Override
    public Optional<MarketSnapshot> fetchMarketByConditionId(String conditionId) {
        return fetchFirst("condition_ids", conditionId);
    }

    private Optional<MarketSnapshot> fetchFirst(String paramName, String paramValue) {
        try {
            List<GammaMarketDto> dtos = gammaWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/markets")
                            .queryParam(paramName, paramValue)
                            .build())
                    .retrieve()
                    .bodyToFlux(GammaMarketDto.class)
                    .collectList()
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (dtos == null || dtos.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(toSnapshot(dtos.get(0)));
        } catch (Exception e) {
            log.warn("Gamma API'den market cekilirken hata olustu ({}={}): {}", paramName, paramValue, e.getMessage());
            return Optional.empty();
        }
    }

    private MarketSnapshot toSnapshot(GammaMarketDto dto) {
        List<String> outcomes = parseStringArray(dto.outcomes());
        List<Double> gammaPrices = parseDoubleArray(dto.outcomePrices());
        List<String> tokenIds = parseStringArray(dto.clobTokenIds());

        Double gammaYesPrice = null;
        Double gammaNoPrice = null;
        String yesTokenId = null;
        String noTokenId = null;
        for (int i = 0; i < outcomes.size(); i++) {
            String outcome = outcomes.get(i);
            Double gammaPrice = i < gammaPrices.size() ? gammaPrices.get(i) : null;
            String tokenId = i < tokenIds.size() ? tokenIds.get(i) : null;
            if ("yes".equalsIgnoreCase(outcome)) {
                gammaYesPrice = gammaPrice;
                yesTokenId = tokenId;
            } else if ("no".equalsIgnoreCase(outcome)) {
                gammaNoPrice = gammaPrice;
                noTokenId = tokenId;
            }
        }

        // Gercek anlik fiyat once CLOB /midpoint'ten denenir (bkz. sinif dokumani
        // -- Gamma'nin outcomePrices'i negRisk marketlerde bayat cikabiliyor);
        // basarisiz olursa Gamma'nin outcomePrices'ina geri dusulur.
        Double yesPrice = fetchClobMidpoint(yesTokenId).orElse(gammaYesPrice);
        Double noPrice = fetchClobMidpoint(noTokenId).orElse(gammaNoPrice);

        // Ikili (Yes/No) marketlerde iki tarafin fiyati toplami ~1.0 olur; ayni
        // tumleyen mantigi ConsensusService.toConsensusMarket'te de kullaniliyor.
        if (yesPrice == null && noPrice != null) {
            yesPrice = 1.0 - noPrice;
        }
        if (noPrice == null && yesPrice != null) {
            noPrice = 1.0 - yesPrice;
        }

        String eventSlug = (dto.events() == null || dto.events().isEmpty()) ? null : dto.events().get(0).slug();

        return new MarketSnapshot(dto.conditionId(), dto.question(), dto.slug(), eventSlug, dto.endDate(), yesPrice, noPrice);
    }

    private Optional<Double> fetchClobMidpoint(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return Optional.empty();
        }
        try {
            ClobMidpointDto midpoint = clobWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/midpoint")
                            .queryParam("token_id", tokenId)
                            .build())
                    .retrieve()
                    .bodyToMono(ClobMidpointDto.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            if (midpoint == null || midpoint.mid() == null) {
                return Optional.empty();
            }
            return Optional.of(Double.parseDouble(midpoint.mid()));
        } catch (Exception e) {
            log.warn("CLOB midpoint alinamadi (tokenId={}): {}", tokenId, e.getMessage());
            return Optional.empty();
        }
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("Gamma outcomes/outcomePrices parse edilemedi: {}", json);
            return List.of();
        }
    }

    private List<Double> parseDoubleArray(String json) {
        return parseStringArray(json).stream().map(Double::parseDouble).collect(Collectors.toList());
    }
}
