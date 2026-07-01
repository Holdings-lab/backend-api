-- V003: broker_accounts에 Hyphen 연동 컬럼 추가 (PostgreSQL)
-- 파일명은 마이그레이션 순서 호환을 위해 유지 (구 connected_id 마이그레이션).
-- 신규 설치(V3_001)에는 이미 포함되어 있으므로 IF NOT EXISTS로 안전하게 적용.

ALTER TABLE broker_accounts
    ADD COLUMN IF NOT EXISTS hyphen_user_id VARCHAR(255);

ALTER TABLE broker_accounts
    ADD COLUMN IF NOT EXISTS hyphen_account_details TEXT;

COMMENT ON COLUMN broker_accounts.hyphen_user_id IS 'AES 암호화된 하이픈 증권사 로그인 사용자 ID';
COMMENT ON COLUMN broker_accounts.hyphen_account_details IS '하이픈 계좌 조회 응답 JSON 전체';

-- 구 CODEF/connected_id 컬럼이 남아 있으면 V004__Rename_codef_to_hyphen.sql 실행
