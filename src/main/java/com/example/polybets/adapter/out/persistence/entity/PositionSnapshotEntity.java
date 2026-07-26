package com.example.polybets.adapter.out.persistence.entity;

import com.example.polybets.domain.model.Category;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "position_snapshot")
public class PositionSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(name = "proxy_wallet", nullable = false, length = 64)
    private String proxyWallet;

    @Column(name = "user_name", length = 100)
    private String userName;

    @Column(name = "condition_id", nullable = false, length = 100)
    private String conditionId;

    @Column(name = "market_title", length = 300)
    private String marketTitle;

    @Column(name = "market_slug", length = 200)
    private String marketSlug;

    @Column(name = "event_slug", length = 200)
    private String eventSlug;

    @Column(length = 50)
    private String outcome;

    @Column(name = "cur_price")
    private Double curPrice;

    @Column(name = "avg_price")
    private Double avgPrice;

    @Column(name = "current_value")
    private Double currentValue;

    @Column(name = "initial_value")
    private Double initialValue;

    @Column(name = "end_date", length = 50)
    private String endDate;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    protected PositionSnapshotEntity() {
        // JPA icin
    }

    public PositionSnapshotEntity(Category category, String proxyWallet, String userName, String conditionId,
                                   String marketTitle, String marketSlug, String eventSlug, String outcome,
                                   Double curPrice, Double avgPrice, Double currentValue, Double initialValue,
                                   String endDate, Instant syncedAt) {
        this.category = category;
        this.proxyWallet = proxyWallet;
        this.userName = userName;
        this.conditionId = conditionId;
        this.marketTitle = marketTitle;
        this.marketSlug = marketSlug;
        this.eventSlug = eventSlug;
        this.outcome = outcome;
        this.curPrice = curPrice;
        this.avgPrice = avgPrice;
        this.currentValue = currentValue;
        this.initialValue = initialValue;
        this.endDate = endDate;
        this.syncedAt = syncedAt;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getProxyWallet() {
        return proxyWallet;
    }

    public String getUserName() {
        return userName;
    }

    public String getConditionId() {
        return conditionId;
    }

    public String getMarketTitle() {
        return marketTitle;
    }

    public String getMarketSlug() {
        return marketSlug;
    }

    public String getEventSlug() {
        return eventSlug;
    }

    public String getOutcome() {
        return outcome;
    }

    public Double getCurPrice() {
        return curPrice;
    }

    public Double getAvgPrice() {
        return avgPrice;
    }

    public Double getCurrentValue() {
        return currentValue;
    }

    public Double getInitialValue() {
        return initialValue;
    }

    public String getEndDate() {
        return endDate;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
