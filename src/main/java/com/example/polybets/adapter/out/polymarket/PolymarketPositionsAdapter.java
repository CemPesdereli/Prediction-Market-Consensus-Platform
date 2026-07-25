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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PositionsPort'un Polymarket Data API implementasyonu.
 * GET /positions?user=&redeemable=false&limit=
 */
@Component
public class PolymarketPositionsAdapter implements PositionsPort {

    private static final Logger log = LoggerFactory.getLogger(PolymarketPositionsAdapter.class);

    /**
     * Kazanan her REDEEM kaydı icin ek bir /activity?type=TRADE cagrisi gerekiyor
     * (bkz. fetchRedeemedPositions) -- bunlari paralel calistirmak icin es zamanli
     * istek sayisi sinirlandiriliyor, Polymarket API'sini bogmadan makul bir surede
     * bitmesi icin.
     */
    private static final int TRADE_HISTORY_CONCURRENCY = 8;

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
            List<ActivityDto> redeems = webClient.get()
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

            if (redeems == null || redeems.isEmpty()) {
                return List.of();
            }

            // Her kazanan pozisyonun gercek net karini hesaplamak icin o marketteki
            // tum alim/satim (TRADE) gecmisi ayrica cekiliyor -- bkz. toRedeemedClosedPosition.
            // Paralel calistiriliyor (TRADE_HISTORY_CONCURRENCY), yoksa N kazanan icin
            // N ekstra sirali HTTP cagrisi cok yavaslatirdi.
            return Flux.fromIterable(redeems)
                    .filter(r -> r.conditionId() != null && r.timestamp() != null)
                    .flatMap(redeem -> fetchTradeHistory(proxyWallet, redeem.conditionId())
                            .map(trades -> toRedeemedClosedPosition(redeem, trades))
                            .onErrorReturn(toRedeemedClosedPosition(redeem, List.of())), TRADE_HISTORY_CONCURRENCY)
                    .collectList()
                    .timeout(Duration.ofSeconds(60))
                    .block();
        } catch (Exception e) {
            log.warn("Redeem gecmisi cekilirken hata olustu (wallet={}): {}", proxyWallet, e.getMessage());
            return List.of();
        }
    }

    /**
     * Bir markette (conditionId) yapilmis tum BUY/SELL islemlerini ceker --
     * kazanan bir pozisyonun net karini hesaplamak icin gereken maliyet gecmisi.
     */
    private Mono<List<ActivityDto>> fetchTradeHistory(String proxyWallet, String conditionId) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/activity")
                        .queryParam("user", proxyWallet)
                        .queryParam("type", "TRADE")
                        .queryParam("market", conditionId)
                        .queryParam("limit", positionsLimit)
                        .build())
                .retrieve()
                .bodyToFlux(ActivityDto.class)
                .collectList()
                .timeout(Duration.ofSeconds(15));
    }

    /**
     * Net kar = (bu marketteki tum SELL islemlerinin toplam usdcSize'i + redeem
     * odemesi) - (tum BUY islemlerinin toplam usdcSize'i). Bu basit nakit-akisi
     * ozdesligi, agirlikli-ortalama-maliyet (WAC) yontemiyle pay-pay hesaplanan
     * sonuçla birebir eslesiyor (gercek bir cuzdanin islem gecmisiyle dogrulandi --
     * bkz. CLAUDE.md), o yuzden pay bazinda WAC replay'ine gerek yok.
     *
     * Guvenlik kontrolu: BUY-SELL net hisse sayisi redeem'de belirtilen hisse
     * sayisiyla (yaklasik) eslesmiyorsa -- ornegin positionsLimit asilip bazi
     * islemler eksik geldiyse -- kar/zarar hesaplanamaz sayilip null birakilir
     * (yanlis bir sayi uydurmaktansa bilinmiyor demek tercih ediliyor).
     */
    private ClosedPosition toRedeemedClosedPosition(ActivityDto redeem, List<ActivityDto> trades) {
        String outcome = redeem.outcome();
        List<ActivityDto> relevant = trades.stream()
                .filter(t -> t.side() != null && t.size() != null && t.usdcSize() != null)
                .filter(t -> outcome == null ? t.outcome() == null : outcome.equalsIgnoreCase(t.outcome()))
                .collect(Collectors.toList());

        double totalCost = relevant.stream()
                .filter(t -> "BUY".equalsIgnoreCase(t.side()))
                .mapToDouble(ActivityDto::usdcSize).sum();
        double totalSellProceeds = relevant.stream()
                .filter(t -> "SELL".equalsIgnoreCase(t.side()))
                .mapToDouble(ActivityDto::usdcSize).sum();
        double netBuyShares = relevant.stream()
                .filter(t -> "BUY".equalsIgnoreCase(t.side()))
                .mapToDouble(ActivityDto::size).sum();
        double netSellShares = relevant.stream()
                .filter(t -> "SELL".equalsIgnoreCase(t.side()))
                .mapToDouble(ActivityDto::size).sum();
        double sharesHeldAtRedeem = netBuyShares - netSellShares;

        double redeemSize = redeem.size() == null ? 0.0 : redeem.size();
        boolean reconstructionReliable = totalCost > 0
                && Math.abs(sharesHeldAtRedeem - redeemSize) < Math.max(0.5, redeemSize * 0.01);

        Double cashPnl = null;
        Double percentPnl = null;
        if (reconstructionReliable) {
            double redeemPayout = redeem.usdcSize() == null ? 0.0 : redeem.usdcSize();
            double totalProceeds = totalSellProceeds + redeemPayout;
            cashPnl = totalProceeds - totalCost;
            percentPnl = (cashPnl / totalCost) * 100.0;
        }

        return new ClosedPosition(
                redeem.proxyWallet(),
                redeem.conditionId(),
                redeem.title(),
                redeem.slug(),
                redeem.eventSlug(),
                redeem.outcome(),
                true,
                cashPnl,
                percentPnl,
                Instant.ofEpochSecond(redeem.timestamp()).toString());
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
                    dto.avgPrice(),
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
}
