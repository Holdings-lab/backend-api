ALTER TABLE asset_positions
    ADD COLUMN IF NOT EXISTS daily_change_pct NUMERIC(10, 4);

COMMENT ON COLUMN asset_positions.daily_change_pct IS 'KIS overseas price rate (daily change %). Not purchase-based gain/loss rate.';
