-- Legacy CODEF naming → Hyphen naming (idempotent: 이미 변경된 DB에서도 통과)

DO $$
BEGIN
    -- codef_status → hyphen_status
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_status'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_status'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN codef_status TO hyphen_status;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_status'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_status'
    ) THEN
        ALTER TABLE broker_accounts DROP COLUMN codef_status;
    END IF;

    -- codef_token_id → hyphen_account_password
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_token_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_account_password'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN codef_token_id TO hyphen_account_password;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_token_id'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_account_password'
    ) THEN
        ALTER TABLE broker_accounts DROP COLUMN codef_token_id;
    END IF;

    -- codef_token_secret → hyphen_user_password
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_token_secret'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_user_password'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN codef_token_secret TO hyphen_user_password;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_token_secret'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_user_password'
    ) THEN
        ALTER TABLE broker_accounts DROP COLUMN codef_token_secret;
    END IF;

    -- connected_id → hyphen_user_id
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'connected_id'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_user_id'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN connected_id TO hyphen_user_id;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'connected_id'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_user_id'
    ) THEN
        ALTER TABLE broker_accounts DROP COLUMN connected_id;
    END IF;

    -- codef_account_details → hyphen_account_details
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_account_details'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_account_details'
    ) THEN
        ALTER TABLE broker_accounts RENAME COLUMN codef_account_details TO hyphen_account_details;
    ELSIF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'codef_account_details'
    ) AND EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'broker_accounts' AND column_name = 'hyphen_account_details'
    ) THEN
        ALTER TABLE broker_accounts DROP COLUMN codef_account_details;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.idx_broker_accounts_status') IS NOT NULL
       AND to_regclass('public.idx_broker_accounts_hyphen_status') IS NULL THEN
        ALTER INDEX idx_broker_accounts_status RENAME TO idx_broker_accounts_hyphen_status;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.codef_sync_history') IS NOT NULL
       AND to_regclass('public.hyphen_sync_history') IS NULL THEN
        ALTER TABLE codef_sync_history RENAME TO hyphen_sync_history;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.idx_codef_sync_history_user_account_status') IS NOT NULL
       AND to_regclass('public.idx_hyphen_sync_history_user_account_status') IS NULL THEN
        ALTER INDEX idx_codef_sync_history_user_account_status
            RENAME TO idx_hyphen_sync_history_user_account_status;
    END IF;

    IF to_regclass('public.idx_codef_sync_history_account_id') IS NOT NULL
       AND to_regclass('public.idx_hyphen_sync_history_account_id') IS NULL THEN
        ALTER INDEX idx_codef_sync_history_account_id
            RENAME TO idx_hyphen_sync_history_account_id;
    END IF;

    IF to_regclass('public.idx_codef_sync_history_status') IS NOT NULL
       AND to_regclass('public.idx_hyphen_sync_history_status') IS NULL THEN
        ALTER INDEX idx_codef_sync_history_status
            RENAME TO idx_hyphen_sync_history_status;
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
WHERE ba.hyphen_status = 'CONNECTED';
