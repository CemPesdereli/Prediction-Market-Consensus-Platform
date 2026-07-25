package com.example.polybets.adapter.out.polymarket;

import com.example.polybets.adapter.out.polymarket.dto.ActivityDto;
import com.example.polybets.adapter.out.polymarket.dto.PositionDto;
import com.example.polybets.domain.model.ActivePosition;
import com.example.polybets.domain.model.ClosedPosition;
import com.example.polybets.domain.port.PositionsPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * PositionsPort'un Polymarket Data API implementasyonu.
 * GET /positions?user=&redeemable=false&limit=
 */
@Component
public class PolymarketPositionsAdapter implements PositionsPort {

    private static final Logger log = LoggerFactory.getLogger(PolymarketPositionsAdapter.class);

    private final WebClient webClient;
    private final int positionsLimit;

    public PolymarketPositionsAdapter(
            WebClient polymarketWebClient,
            @Value("${polymarket.positions-limit}") int positionsLimit) {
        this.webClient = polymarketWebClient;
        this.positionsLimit = positionsLimit;
    }

    @Override
    public List<ActivePosition> fetchActivePositions(String proxyWallet) {
        try {
            List<PositionDto> dtos = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/positions")
                            .queryParam("user", proxyWallet)
                            .queryParam("redeemable", false)
                            .queryParam("limit", positionsLimit)
                            .build())
                    .retrieve()
                    .bodyToFlux(PositionDto.class)
                    .collectList()
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return toDomain(dtos == null ? List.of() : dtos);
        } catch (Exception e) {
            log.warn("Pozisyonlar cekilirken hata olustu (wallet={}): {}", proxyWallet, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ClosedPosition> fetchClosedPositions(String proxyWallet) {
        try {
            List<PositionDto> dtos = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/positions")
                            .queryParam("user", proxyWallet)
                            .queryParam("redeemable", true)
                            .queryParam("limit", positionsLimit)
                            .build())
                    .retrieve()
                    .bodyToFlux(PositionDto.class)
                    .collectList()
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return toClosedDomain(dtos == null ? List.of() : dtos);
        } catch (Exception e) {
            log.warn("Kapanmis pozisyonlar cekilirken hata olustu (wallet={}): {}", proxyWallet, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ClosedPosition> fetchRedeemedPositions(String proxyWallet, Instant since) {
        try {
            List<ActivityDto> dtos = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/activity")
                            .queryParam("user", proxyWallet)
                            .queryParam("type", "REDEEM")
                            .queryParam("start", since.getEpochSecond())
                            .queryParam("limit", positionsLimit)
                            .build())
                    .retrieve()
                    .bodyToFlux(ActivityDto.class)
                    .collectList()
                    .timeout(Duration.ofSeconds(15))
                    .block();

            return toRedeemedDomain(dtos == null ? List.of() : dtos);
        } catch (Exception e) {
            log.warn("Redeem gecmisi cekilirken hata olustu (wallet={}): {}", proxyWallet, e.getMessage());
            return List.of();
        }
    }

    private List<ActivePosition> toDomain(List<PositionDto> dtos) {
        List<ActivePosition> positions = new ArrayList<>();
        for (PositionDto dto : dtos) {
            if (dto.conditionId() == null) {
                continue;
            }
            positions.add(new ActivePosition(
                    dto.proxyWallet(),
                    dto.conditionId(),
                    dto.title(),
                    dto.slug(),
                    dto.eventSlug(),
                    dto.outcome(),
                    dto.curPrice(),
                    dto.currentValue(),
                    dto.endDate()));
        }
        return positions;
    }

    private List<ClosedPosition> toClosedDomain(List<PositionDto> dtos) {
        List<ClosedPosition> positions = new ArrayList<>();
        for (PositionDto dto : dtos) {
            if (dto.conditionId() == null) {
                continue;
            }
            // /positions?redeemable=true pratikte neredeyse tamami KAYBEDILEN, hic
            // claim edilmemis (degeri $0'a dusmus) bahisler -- kazananlar claim
            // edilir edilmez /positions'tan tamamen kayboluyor (bkz. fetchRedeemedPositions).
            positions.add(new ClosedPosition(
                    dto.proxyWallet(),
                    dto.conditionId(),
                    dto.title(),
                    dto.slug(),
                    dto.eventSlug(),
                    dto.outcome(),
                    false,
                    dto.cashPnl(),
                    dto.percentPnl(),
                    dto.endDate()));
        }
        return positions;
    }

    private List<ClosedPosition> toRedeemedDomain(List<ActivityDto> dtos) {
        List<ClosedPosition> positions = new ArrayList<>();
        for (ActivityDto dto : dtos) {
            if (dto.conditionId() == null || dto.timestamp() == null) {
                continue;
            }
            // REDEEM aktivitesi pratikte neredeyse tamami KAZANILAN bahisler --
            // kaybeden pozisyonu claim etmenin bir anlami yok (degeri $0).
            positions.add(new ClosedPosition(
                    dto.proxyWallet(),
                    dto.conditionId(),
                    dto.title(),
                    dto.slug(),
                    dto.eventSlug(),
                    dto.outcome(),
                    true,
                    null, // net kar bilinmiyor -- brut redeem tutari (usdcSize) maliyet dusulmeden kar sayilamaz
                    null,
                    Instant.ofEpochSecond(dto.timestamp()).toString()));
        }
        return positions;
    }
}
