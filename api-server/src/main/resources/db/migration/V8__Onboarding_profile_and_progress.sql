-- 온보딩: 투자 성향/관심사/진척도 + InvestmentHorizon 값 rename

ALTER TABLE user_investment_profiles
    ALTER COLUMN investment_horizon DROP NOT NULL;

ALTER TABLE user_investment_profiles
    ALTER COLUMN max_drawdown_tolerance DROP NOT NULL;

ALTER TABLE user_investment_profiles
    ALTER COLUMN investment_horizon SET DEFAULT NULL;

UPDATE user_investment_profiles
SET investment_horizon = 'Y1_3'
WHERE investment_horizon = 'ONE_TO_THREE_YEARS';

UPDATE user_investment_profiles
SET investment_horizon = 'Y3_5'
WHERE investment_horizon = 'THREE_TO_FIVE_YEARS';

UPDATE user_investment_profiles
SET investment_horizon = 'OVER_5Y'
WHERE investment_horizon = 'OVER_FIVE_YEARS';

ALTER TABLE user_investment_profiles
    ADD COLUMN IF NOT EXISTS investment_style VARCHAR(30);

CREATE TABLE IF NOT EXISTS user_interest_sectors (
    user_id BIGINT NOT NULL,
    sector VARCHAR(40) NOT NULL,
    PRIMARY KEY (user_id, sector)
);

CREATE TABLE IF NOT EXISTS user_onboarding_progress (
    user_id BIGINT PRIMARY KEY,
    account_skipped BOOLEAN NOT NULL DEFAULT FALSE,
    draft_financial_goal VARCHAR(30),
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
