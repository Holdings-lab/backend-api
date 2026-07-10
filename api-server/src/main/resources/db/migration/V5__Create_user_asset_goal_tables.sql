-- 사용자 자산 지표, 목표, 세션 상태

CREATE TABLE IF NOT EXISTS user_investment_profiles (
    user_id BIGINT PRIMARY KEY,
    investment_horizon VARCHAR(30) NOT NULL DEFAULT 'ONE_TO_THREE_YEARS',
    max_drawdown_tolerance INTEGER NOT NULL DEFAULT 10,
    onboarded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_investment_goals (
    user_id BIGINT PRIMARY KEY,
    financial_goal VARCHAR(30) NOT NULL,
    goal_label VARCHAR(100) NOT NULL,
    target_amount NUMERIC(18, 2) NOT NULL,
    goal_start_amount NUMERIC(18, 2) NOT NULL,
    goal_start_date DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_asset_ath (
    user_id BIGINT PRIMARY KEY,
    all_time_high_amount NUMERIC(18, 2) NOT NULL,
    all_time_high_date DATE NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS user_asset_snapshots (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    snapshot_type VARCHAR(30) NOT NULL,
    asset_total NUMERIC(18, 2) NOT NULL,
    snapshot_date DATE NOT NULL,
    snapshot_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, snapshot_type, snapshot_date)
);

CREATE INDEX IF NOT EXISTS idx_user_asset_snapshots_user_type_date
    ON user_asset_snapshots(user_id, snapshot_type, snapshot_date DESC);

CREATE TABLE IF NOT EXISTS user_sync_state (
    user_id BIGINT PRIMARY KEY,
    last_hyphen_sync_at TIMESTAMP,
    last_client_heartbeat_at TIMESTAMP,
    session_status VARCHAR(20) NOT NULL DEFAULT 'EXPIRED',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
