package com.example.polybets.adapter.out.persistence.entity;

import com.example.polybets.domain.model.WatchedBetStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "watched_bet")
public class WatchedBetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "condition_id", nullable = false, length = 100)
    private String conditionId;

    @Column(name = "market_title", length = 300)
    private String marketTitle;

    @Column(name = "market_slug", length = 200)
    private String marketSlug;

    @Column(name = "event_slug", length = 200)
    private String eventSlug;

    @Column(nullable = false, length = 10)
    private String outcome;

    @Column(name = "entry_price", nullable = false)
    private double entryPrice;

    @Column(name = "target_price", nullable = false)
    private double targetPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WatchedBetStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "last_checked_price")
    private Double lastCheckedPrice;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

    protected WatchedBetEntity() {
        // JPA icin
    }

    public WatchedBetEntity(Long id, String conditionId, String marketTitle, String marketSlug, String eventSlug,
                             String outcome, double entryPrice, double targetPrice, WatchedBetStatus status,
                             Instant createdAt, Instant triggeredAt, Double lastCheckedPrice, Instant lastCheckedAt) {
        this.id = id;
        this.conditionId = conditionId;
        this.marketTitle = marketTitle;
        this.marketSlug = marketSlug;
        this.eventSlug = eventSlug;
        this.outcome = outcome;
        this.entryPrice = entryPrice;
        this.targetPrice = targetPrice;
        this.status = status;
        this.createdAt = createdAt;
        this.triggeredAt = triggeredAt;
        this.lastCheckedPrice = lastCheckedPrice;
        this.lastCheckedAt = lastCheckedAt;
    }

    public Long getId() {
        return id;
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

    public double getEntryPrice() {
        return entryPrice;
    }

    public double getTargetPrice() {
        return targetPrice;
    }

    public WatchedBetStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getTriggeredAt() {
        return triggeredAt;
    }

    public Double getLastCheckedPrice() {
        return lastCheckedPrice;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }
}
