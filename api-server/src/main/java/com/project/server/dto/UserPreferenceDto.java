package com.project.server.dto;

import lombok.Data;

public class UserPreferenceDto {

    @Data
    public static class UpdateSettingsRequest {
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
