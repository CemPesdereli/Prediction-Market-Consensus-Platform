package com.example.polybets.domain.model;

/**
 * Bir kategorinin aylık leaderboard'undaki tek bir trader.
 * Saf domain nesnesi — JPA/Jackson annotation'ı yok.
 */
public record Trader(
        String proxyWallet,
        String userName,
        int rank,
        double pnl,
        double vol
) {
    /**
     * ROI proxy'si: dönemsel kârın işlem hacmine oranı.
     * Polymarket API'si dogrudan bir "ROI" alanı sunmuyor; bu, hacme göre
     * verimliliği ölçen makul bir yaklaşıklık. vol=0 ise (teorik olarak
     * olmamalı ama savunmacı programlama) 0 döner.
     */
    public double roi() {
        return vol == 0.0 ? 0.0 : pnl / vol;
    }
}
