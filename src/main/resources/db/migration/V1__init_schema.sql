CREATE TABLE leaderboard_entry (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(20) NOT NULL,
    rank INTEGER NOT NULL,
    proxy_wallet VARCHAR(64) NOT NULL,
    user_name VARCHAR(100),
    vol DOUBLE PRECISION,
    pnl DOUBLE PRECISION,
    synced_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_leaderboard_category ON leaderboard_entry (category);

CREATE TABLE position_snapshot (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(20) NOT NULL,
    proxy_wallet VARCHAR(64) NOT NULL,
    user_name VARCHAR(100),
    condition_id VARCHAR(100) NOT NULL,
    market_title VARCHAR(300),
    market_slug VARCHAR(200),
    event_slug VARCHAR(200),
    outcome VARCHAR(50),
    cur_price DOUBLE PRECISION,
    current_value DOUBLE PRECISION,
    end_date VARCHAR(50),
    synced_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_position_category ON position_snapshot (category);
CREATE INDEX idx_position_condition ON position_snapshot (condition_id);
