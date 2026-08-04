-- 설정 탭: 정책 변화 알림 + 브리핑 시각

ALTER TABLE user_notification_settings
    ADD COLUMN IF NOT EXISTS policy_change_alert BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE user_notification_settings
    ADD COLUMN IF NOT EXISTS briefing_time VARCHAR(5) NOT NULL DEFAULT '09:00';

UPDATE user_notification_settings
SET policy_change_alert = important_event_briefing;
