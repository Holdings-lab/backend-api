package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

public class UserPreferenceDto {

    @Data
    @Builder
    public static class NotificationSettingsResponse {
        private boolean before30m;
        private boolean importantEventBriefing;
        private boolean learningReminder;
    }

    @Data
    public static class UpdateNotificationSettingsRequest {
        private boolean before30m;
        private boolean importantEventBriefing;
        private boolean learningReminder;

        public boolean getBefore30m() {
            return before30m;
        }

        public boolean getImportantEventBriefing() {
            return importantEventBriefing;
        }

        public boolean getLearningReminder() {
            return learningReminder;
        }
    }
}
