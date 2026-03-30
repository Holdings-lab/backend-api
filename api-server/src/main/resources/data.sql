MERGE INTO users (id, username, nickname, password, fcm_token)
KEY (id)
VALUES
(1, 'jiyoung', '지웅', 'password123', NULL),
(2, 'demo_user', '데모', 'demo1234', NULL);

MERGE INTO policy_events (id, title, keyword, impact_score, analysis_summary, created_at)
KEY (id)
VALUES
(1, '한국은행 기준금리 0.25%p 인상', '금리인상', 8.5, '대출 이자 부담 증가 및 소비 위축 예상', CURRENT_TIMESTAMP() - 1),
(2, '부동산 규제 완화 정책 발표', '부동산', 7.2, '주택 거래량 일시적 증가 예상', CURRENT_TIMESTAMP()),
(101, '미국 비농업고용', '고용', 8.1, '고용 지표가 금리 기대에 직접 영향을 줄 수 있음', CURRENT_TIMESTAMP());

MERGE INTO user_notification_settings (id, user_id, before_30m, important_event_briefing, learning_reminder, updated_at)
KEY (id)
VALUES
(1, 1, TRUE, FALSE, TRUE, CURRENT_TIMESTAMP()),
(2, 2, TRUE, FALSE, TRUE, CURRENT_TIMESTAMP());

MERGE INTO user_event_alerts (id, user_id, event_id, enabled, updated_at)
KEY (id)
VALUES
(1, 1, 101, FALSE, CURRENT_TIMESTAMP());

MERGE INTO user_profiles (id, user_id, avatar_text, weekly_learning_count, quiz_accuracy_percent, weak_topic)
KEY (id)
VALUES
(1, 1, 'JY', 6, 82, '환율'),
(2, 2, 'DM', 3, 71, '고용');

MERGE INTO user_watch_assets (id, user_id, asset_name, change_percent, signal_text, display_order)
KEY (id)
VALUES
(1, 1, '장기채 ETF', -1.2, '변동성↑ 74%', 1),
(2, 1, '나스닥 성장주 ETF', -0.8, '하락확률 62%', 2),
(3, 1, '달러 인덱스 ETF', 0.6, '상승확률 68%', 3),
(4, 2, '장기채 ETF', -0.3, '보합권 50%', 1);
