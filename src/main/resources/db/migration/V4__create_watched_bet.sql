CREATE TABLE watched_bet (
    id BIGSERIAL PRIMARY KEY,
    condition_id VARCHAR(100) NOT NULL,
    market_title VARCHAR(300),
    market_slug VARCHAR(200),
    event_slug VARCHAR(200),
    outcome VARCHAR(10) NOT NULL,
    entry_price DOUBLE PRECISION NOT NULL,
    target_price DOUBLE PRECISION NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    triggered_at TIMESTAMP,
    last_checked_price DOUBLE PRECISION,
    last_checked_at TIMESTAMP
);

CREATE INDEX idx_watched_bet_status ON watched_bet (status);
