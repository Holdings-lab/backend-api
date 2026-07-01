-- broker_accounts Hyphen 연동 컬럼 (V1 이전 스키마 호환)

ALTER TABLE broker_accounts
    ADD COLUMN IF NOT EXISTS hyphen_user_id VARCHAR(255);

ALTER TABLE broker_accounts
    ADD COLUMN IF NOT EXISTS hyphen_account_details TEXT;

COMMENT ON COLUMN broker_accounts.hyphen_user_id IS 'AES 암호화된 하이픈 증권사 로그인 사용자 ID';
COMMENT ON COLUMN broker_accounts.hyphen_account_details IS '하이픈 계좌 조회 응답 JSON 전체';
