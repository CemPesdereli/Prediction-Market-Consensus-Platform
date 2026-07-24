package com.example.polybets.adapter.out.persistence.entity;

import com.example.polybets.domain.model.Category;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "leaderboard_entry")
public class LeaderboardEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false)
    private Integer rank;

    @Column(name = "proxy_wallet", nullable = false, length = 64)
    private String proxyWallet;

    @Column(name = "user_name", length = 100)
    private String userName;

    private Double vol;

    private Double pnl;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected LeaderboardEntryEntity() {
        // JPA icin
    }

    public LeaderboardEntryEntity(Category category, Integer rank, String proxyWallet, String userName,
                                   Double vol, Double pnl, Instant syncedAt) {
        this.category = category;
        this.rank = rank;
        this.proxyWallet = proxyWallet;
        this.userName = userName;
        this.vol = vol;
        this.pnl = pnl;
        this.syncedAt = syncedAt;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public Integer getRank() {
        return rank;
    }

    public String getProxyWallet() {
        return proxyWallet;
    }

    public String getUserName() {
        return userName;
    }

    public Double getVol() {
        return vol;
    }

    public Double getPnl() {
        return pnl;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
