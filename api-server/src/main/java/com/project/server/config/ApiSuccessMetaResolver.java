package com.project.server.config;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import java.util.List;

@Component
public class ApiSuccessMetaResolver {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<ApiSuccessMetaRule> RULES = List.of(
            new ApiSuccessMetaRule(HttpMethod.POST, "/api/auth/register", "AUTH-REGISTER-200", "회원가입이 완료되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/api/auth/login", "AUTH-LOGIN-200", "로그인에 성공했습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/api/auth/register-fcm-token", "AUTH-FCM-200", "FCM 토큰이 등록되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.PATCH, "/api/auth/users/*/nickname", "AUTH-NICKNAME-200", "닉네임이 변경되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/api/auth/users/*/change-password", "AUTH-PASSWORD-200", "비밀번호가 변경되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.DELETE, "/api/auth/delete/*", "AUTH-DELETE-200", "회원 탈퇴가 완료되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.GET, "/api/auth/accounts", "AUTH-ACCOUNTS-200", "계정 목록 조회에 성공했습니다."),

            new ApiSuccessMetaRule(HttpMethod.GET, "/api/home", "HOME-GET-200", "홈 정보 조회에 성공했습니다."),
            new ApiSuccessMetaRule(HttpMethod.GET, "/api/home/briefing", "HOME-BRIEFING-200", "홈 브리핑 조회에 성공했습니다."),
                new ApiSuccessMetaRule(HttpMethod.GET, "/api/health", "HEALTH-GET-200", "헬스체크에 성공했습니다."),

            new ApiSuccessMetaRule(HttpMethod.GET, "/api/events", "EVENTS-GET-200", "이벤트 목록 조회에 성공했습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/api/events/refresh", "EVENTS-REFRESH-200", "이벤트 새로고침에 성공했습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/api/events/*/alerts", "EVENTS-ALERT-200", "이벤트 알림 설정이 저장되었습니다."),

            new ApiSuccessMetaRule(HttpMethod.GET, "/api/insights/heatmap", "INSIGHTS-HEATMAP-200", "히트맵 조회에 성공했습니다."),

            new ApiSuccessMetaRule(HttpMethod.GET, "/api/me", "ME-GET-200", "내 정보 조회에 성공했습니다."),
            new ApiSuccessMetaRule(HttpMethod.GET, "/api/me/settings/notifications", "ME-NOTIFICATION-GET-200", "알림 설정 조회에 성공했습니다."),
            new ApiSuccessMetaRule(HttpMethod.PATCH, "/api/me/settings/notifications", "ME-NOTIFICATION-PATCH-200", "알림 설정이 저장되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.GET, "/api/me/watch-assets/options", "ME-ASSET-OPTIONS-200", "관심자산 옵션 조회에 성공했습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/api/me/watch-assets", "ME-ASSET-UPDATE-200", "관심자산이 업데이트되었습니다."),

            new ApiSuccessMetaRule(HttpMethod.POST, "/api/ai/trigger", "AI-TRIGGER-200", "AI 파이프라인 실행에 성공했습니다."),
                new ApiSuccessMetaRule(HttpMethod.POST, "/api/ai/train-regression", "AI-TRAIN-200", "회귀 학습 실행 요청에 성공했습니다."),
                new ApiSuccessMetaRule(HttpMethod.POST, "/api/content/policy-feed", "CONTENT-FEED-200", "정책 피드 조회에 성공했습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/api/internal/webhook/event", "WEBHOOK-EVENT-200", "Webhook 처리가 완료되었습니다."),

            // Admin APIs
            new ApiSuccessMetaRule(HttpMethod.POST, "/admin/accounts/add", "ADMIN-ACCOUNT-ADD-200", "계정이 성공적으로 추가되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/admin/accounts/delete/*", "ADMIN-ACCOUNT-DELETE-200", "계정이 성공적으로 삭제되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.GET, "/admin/accounts/list", "ADMIN-ACCOUNT-LIST-200", "사용자 목록 조회에 성공했습니다."),
            new ApiSuccessMetaRule(HttpMethod.PATCH, "/admin/accounts/*/fcm-token", "ADMIN-FCM-UPDATE-200", "FCM 토큰이 업데이트되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/admin/accounts/change-password", "ADMIN-PASSWORD-200", "비밀번호가 변경되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.POST, "/admin/notifications/send", "ADMIN-NOTIFICATION-SEND-200", "알림이 성공적으로 전송되었습니다."),
            new ApiSuccessMetaRule(HttpMethod.GET, "/admin/health", "ADMIN-HEALTH-200", "Admin API 상태 확인에 성공했습니다.")
    );

    public ApiSuccessMeta resolve(HttpMethod method, String path) {
        for (ApiSuccessMetaRule rule : RULES) {
            if (rule.method() == method && pathMatcher.match(rule.pathPattern(), path)) {
                return new ApiSuccessMeta(rule.code(), rule.message());
            }
        }
        return new ApiSuccessMeta("SUCCESS-200", "요청에 성공했습니다.");
    }

    public record ApiSuccessMeta(String code, String message) {}

    private record ApiSuccessMetaRule(HttpMethod method, String pathPattern, String code, String message) {}
}