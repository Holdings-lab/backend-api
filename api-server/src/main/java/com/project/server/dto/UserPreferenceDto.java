package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

public class UserPreferenceDto {

    @Data
    public static class UpdateNotificationSettingsRequest {
        private Boolean policyChangeAlert;
        private String briefingTime;
    }

    @Data
    @Builder
    public static class NotificationSettingsResponse {
        private boolean policyChangeAlert;
        private String briefingTime;
    }

    @Data
    @Builder
    public static class SettingsHomeResponse {
        private SettingsUser user;
        private NotificationSettingsResponse notifications;
        private SettingsInvestment investment;
    }

    @Data
    @Builder
    public static class SettingsUser {
        private String nickname;
        private String email;
        private String avatarText;
    }

    @Data
    @Builder
    public static class SettingsInvestment {
        private SettingsGoal goal;
        private ConnectedAccountsSummary connectedAccounts;
        private InterestsSummary interests;
    }

    @Data
    @Builder
    public static class SettingsGoal {
        private String code;
        private String label;
    }

    @Data
    @Builder
    public static class ConnectedAccountsSummary {
        private long count;
        private long expiredCount;
    }

    @Data
    @Builder
    public static class InterestsSummary {
        private int count;
    }

    @Data
    @Builder
    public static class TestNotificationResponse {
        private String status;
        private String message;
    }
}
