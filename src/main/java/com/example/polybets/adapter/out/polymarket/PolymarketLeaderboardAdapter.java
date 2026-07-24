package com.example.polybets.adapter.out.polymarket;

import com.example.polybets.adapter.out.polymarket.dto.LeaderboardEntryDto;
import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.Trader;
import com.example.polybets.domain.port.LeaderboardPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * LeaderboardPort'un Polymarket Data API implementasyonu.
 * GET /v1/leaderboard?category=&timePeriod=MONTH&orderBy=PNL&limit=
 */
@Component
public class PolymarketLeaderboardAdapter implements LeaderboardPort {

    private static final Logger log = LoggerFactory.getLogger(PolymarketLeaderboardAdapter.class);

    private final WebClient webClient;
    private final String timePeriod;
    private final String orderBy;

    public PolymarketLeaderboardAdapter(
            WebClient polymarketWebClient,
            @Value("${polymarket.time-period}") String timePeriod,
            @Value("${polymarket.order-by}") String orderBy) {
        this.webClient = polymarketWebClient;
        this.timePeriod = timePeriod;
        this.orderBy = orderBy;
    }

    @Override
    public List<Trader> fetchMonthlyLeaderboard(Category category, int limit) {
        try {
            List<LeaderboardEntryDto> dtos = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/leaderboard")
                            .queryParam("category", category.name())
                            .queryParam("timePeriod", timePeriod)
                            .queryParam("orderBy", orderBy)
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToFlux(LeaderboardEntryDto.class)
                    .collectList()
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return toDomain(dtos == null ? List.of() : dtos);
        } catch (Exception e) {
            log.warn("Leaderboard cekilirken hata olustu (category={}): {}", category, e.getMessage());
            return List.of();
        }
    }

    private List<Trader> toDomain(List<LeaderboardEntryDto> dtos) {
        List<Trader> traders = new java.util.ArrayList<>();
        int rank = 1;
        for (LeaderboardEntryDto dto : dtos) {
            traders.add(new Trader(
                    dto.proxyWallet(),
                    dto.userName(),
                    rank++,
                    dto.pnl() == null ? 0.0 : dto.pnl(),
                    dto.vol() == null ? 0.0 : dto.vol()));
        }
        return traders;
    }
}
