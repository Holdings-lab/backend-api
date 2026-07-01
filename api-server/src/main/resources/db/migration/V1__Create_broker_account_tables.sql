-- Hyphen(하이픈) 증권사 계좌 연동 테이블 (users 등은 JPA가 먼저 생성)

CREATE TABLE IF NOT EXISTS broker_accounts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    broker_name VARCHAR(50) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    account_nickname VARCHAR(100),
    hyphen_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    hyphen_account_password VARCHAR(255),
    hyphen_user_password VARCHAR(500),
    hyphen_user_id VARCHAR(255),
    hyphen_account_details TEXT,
    account_owner_name VARCHAR(100),
    account_type VARCHAR(20),
    is_primary BOOLEAN DEFAULT FALSE,
    last_synced_at TIMESTAMP,
    sync_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, broker_name, account_number)
);

CREATE INDEX IF NOT EXISTS idx_broker_accounts_user_id ON broker_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_broker_accounts_hyphen_status ON broker_accounts(hyphen_status);
CREATE INDEX IF NOT EXISTS idx_broker_accounts_last_synced ON broker_accounts(last_synced_at);

CREATE TABLE IF NOT EXISTS asset_positions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    position_type VARCHAR(20),
    quantity NUMERIC(15, 2),
    purchase_price NUMERIC(15, 2),
    current_price NUMERIC(15, 2),
    current_value NUMERIC(18, 2),
    purchase_amount NUMERIC(18, 2),
    gain_loss NUMERIC(18, 2),
    gain_loss_rate NUMERIC(10, 4),
    currency_code VARCHAR(3),
    purchased_at DATE,
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_asset_positions_account_id ON asset_positions(account_id);
CREATE INDEX IF NOT EXISTS idx_asset_positions_user_id ON asset_positions(user_id);
CREATE INDEX IF NOT EXISTS idx_asset_positions_account_symbol ON asset_positions(account_id, symbol);

CREATE TABLE IF NOT EXISTS account_balances (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    total_asset_value NUMERIC(18, 2),
    cash_balance NUMERIC(18, 2),
    deposit_amount NUMERIC(18, 2),
    evaluation_amount NUMERIC(18, 2),
    gain_loss NUMERIC(18, 2),
    gain_loss_rate NUMERIC(10, 4),
    daily_gain_loss NUMERIC(18, 2),
    daily_gain_loss_rate NUMERIC(10, 4),
    as_of_date DATE,
    last_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_account_balances_user_account_date ON account_balances(user_id, account_id, as_of_date);
CREATE INDEX IF NOT EXISTS idx_account_balances_account_id ON account_balances(account_id);

CREATE TABLE IF NOT EXISTS hyphen_sync_history (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    sync_type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(20),
    error_message VARCHAR(500),
    record_count INTEGER,
    sync_duration_ms INTEGER,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_hyphen_sync_history_user_account_status ON hyphen_sync_history(user_id, account_id, status);
CREATE INDEX IF NOT EXISTS idx_hyphen_sync_history_account_id ON hyphen_sync_history(account_id);
CREATE INDEX IF NOT EXISTS idx_hyphen_sync_history_status ON hyphen_sync_history(status);

CREATE OR REPLACE VIEW user_total_assets AS
SELECT
    ba.user_id,
    ba.broker_name,
    ba.account_number,
    ba.account_nickname,
    ab.total_asset_value,
    ab.cash_balance,
    ab.deposit_amount,
    ab.evaluation_amount,
    ab.gain_loss,
    ab.gain_loss_rate,
    ab.daily_gain_loss,
    ab.daily_gain_loss_rate,
    ab.as_of_date,
    ba.last_synced_at
FROM broker_accounts ba
LEFT JOIN account_balances ab ON ba.id = ab.account_id
WHERE ba.hyphen_status = 'CONNECTED';
