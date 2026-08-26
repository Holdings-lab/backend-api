-- Hyphen vendor columns → broker-neutral connection columns.
-- V3는 유지한다. 기존 hyphen 로그인 암호는 한투 키와 의미가 달라서 이전하지 않는다.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_status'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'connection_status'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN hyphen_status TO connection_status;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_user_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'app_key'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN hyphen_user_id TO app_key;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_user_password'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'app_secret'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN hyphen_user_password TO app_secret;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_account_password'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'account_product_code'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN hyphen_account_password TO account_product_code;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_account_details'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'account_details'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN hyphen_account_details TO account_details;
    END IF;
END $$;

ALTER TABLE broker_accounts
    ADD COLUMN IF NOT EXISTS credential_source VARCHAR(20);

ALTER TABLE broker_accounts
    ALTER COLUMN app_key TYPE VARCHAR(500);

UPDATE broker_accounts
SET app_key = NULL,
    app_secret = NULL,
    account_product_code = '01',
    credential_source = COALESCE(credential_source, 'ENV');

ALTER TABLE broker_accounts
    ALTER COLUMN account_product_code TYPE VARCHAR(10);

COMMENT ON COLUMN broker_accounts.app_key IS 'AES 암호화된 한투 appkey. ENV(KIS_MOCK_*) 사용 시 NULL';
COMMENT ON COLUMN broker_accounts.app_secret IS 'AES 암호화된 한투 appsecret. ENV 사용 시 NULL';
COMMENT ON COLUMN broker_accounts.account_product_code IS '한투 계좌상품코드 ACNT_PRDT_CD (2자리)';
COMMENT ON COLUMN broker_accounts.account_details IS '계좌 스냅샷 JSON';
COMMENT ON COLUMN broker_accounts.credential_source IS 'ENV | USER';

DO $$
BEGIN
    IF to_regclass('public.idx_broker_accounts_hyphen_status') IS NOT NULL
       AND to_regclass('public.idx_broker_accounts_connection_status') IS NULL THEN
        ALTER INDEX idx_broker_accounts_hyphen_status RENAME TO idx_broker_accounts_connection_status;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.hyphen_sync_history') IS NOT NULL
       AND to_regclass('public.broker_sync_history') IS NULL THEN
        ALTER TABLE hyphen_sync_history RENAME TO broker_sync_history;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.idx_hyphen_sync_history_user_account_status') IS NOT NULL
       AND to_regclass('public.idx_broker_sync_history_user_account_status') IS NULL THEN
        ALTER INDEX idx_hyphen_sync_history_user_account_status
            RENAME TO idx_broker_sync_history_user_account_status;
    END IF;

    IF to_regclass('public.idx_hyphen_sync_history_account_id') IS NOT NULL
       AND to_regclass('public.idx_broker_sync_history_account_id') IS NULL THEN
        ALTER INDEX idx_hyphen_sync_history_account_id
            RENAME TO idx_broker_sync_history_account_id;
    END IF;

    IF to_regclass('public.idx_hyphen_sync_history_status') IS NOT NULL
       AND to_regclass('public.idx_broker_sync_history_status') IS NULL THEN
        ALTER INDEX idx_hyphen_sync_history_status
            RENAME TO idx_broker_sync_history_status;
    END IF;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_sync_state' AND column_name = 'last_hyphen_sync_at'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'user_sync_state' AND column_name = 'last_broker_sync_at'
    ) THEN
        ALTER TABLE user_sync_state RENAME COLUMN last_hyphen_sync_at TO last_broker_sync_at;
    END IF;
END $$;

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
WHERE ba.connection_status = 'CONNECTED';
