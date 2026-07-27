package com.example.polybets.domain.port;

import com.example.polybets.domain.model.MarketSnapshot;

import java.util.Optional;

/**
 * Belirli bir cüzdana bağlı olmayan, tek bir Polymarket marketinin anlık
 * durumunu (fiyatlar dahil) getiren port. WatchedBet özelliği için gerekli --
 * PositionsPort'un aksine bir kullanıcı cüzdanı gerektirmez, sadece market
 * kimliği (slug ya da conditionId) yeterlidir.
 */
public interface MarketPricePort {

    /**
     * Kullanıcı yeni bir alarm kurarken market slug'ından (Polymarket URL'sinin
     * son parçası) marketi çözümlemek için kullanılır.
     */
    Optional<MarketSnapshot> fetchMarketBySlug(String slug);

    /**
     * Zamanlanmış alarm kontrolünde, zaten bilinen conditionId için anlık
     * fiyatı yeniden çekmek için kullanılır.
     */
    Optional<MarketSnapshot> fetchMarketByConditionId(String conditionId);
}
