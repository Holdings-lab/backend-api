ALTER TABLE asset_positions
    ADD COLUMN IF NOT EXISTS fx_rate NUMERIC(18, 6);

ALTER TABLE asset_positions
    ADD COLUMN IF NOT EXISTS native_purchase_amount NUMERIC(18, 4);

ALTER TABLE asset_positions
    ADD COLUMN IF NOT EXISTS native_valuation_amount NUMERIC(18, 4);

ALTER TABLE asset_positions
    ADD COLUMN IF NOT EXISTS native_gain_loss NUMERIC(18, 4);

ALTER TABLE account_balances
    ADD COLUMN IF NOT EXISTS currency_code VARCHAR(3);

ALTER TABLE account_balances
    ADD COLUMN IF NOT EXISTS fx_rates JSONB;

UPDATE account_balances
SET currency_code = 'KRW'
WHERE currency_code IS NULL;
