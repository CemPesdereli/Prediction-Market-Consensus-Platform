package com.example.polybets.domain.port;

import com.example.polybets.domain.model.Category;
import com.example.polybets.domain.model.Trader;

import java.util.List;

/**
 * Bir kategori için aylık leaderboard verisini getiren port.
 * Bugün Polymarket Data API'si tarafından implemente ediliyor; yarın başka bir
 * platform (Kalshi, Manifold vb.) için ayrı bir adapter yazılabilir.
 */
public interface LeaderboardPort {

    List<Trader> fetchMonthlyLeaderboard(Category category, int limit);
}
