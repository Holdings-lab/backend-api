-- V004: Legacy CODEF naming → Hyphen naming (PostgreSQL)
-- 기존 DB에 codef_* / connected_id / codef_sync_history 가 있을 때 실행

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_status'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN codef_status TO hyphen_status;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_token_id'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN codef_token_id TO hyphen_account_password;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_token_secret'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN codef_token_secret TO hyphen_user_password;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'connected_id'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN connected_id TO hyphen_user_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_account_details'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN codef_account_details TO hyphen_account_details;
    END IF;
END $$;

ALTER INDEX IF EXISTS idx_broker_accounts_status RENAME TO idx_broker_accounts_hyphen_status;

DO $$
BEGIN
    IF to_regclass('public.codef_sync_history') IS NOT NULL
       AND to_regclass('public.hyphen_sync_history') IS NULL THEN
        ALTER TABLE codef_sync_history RENAME TO hyphen_sync_history;
    END IF;
END $$;

ALTER INDEX IF EXISTS idx_codef_sync_history_user_account_status
    RENAME TO idx_hyphen_sync_history_user_account_status;
ALTER INDEX IF EXISTS idx_codef_sync_history_account_id
    RENAME TO idx_hyphen_sync_history_account_id;
ALTER INDEX IF EXISTS idx_codef_sync_history_status
    RENAME TO idx_hyphen_sync_history_status;

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
