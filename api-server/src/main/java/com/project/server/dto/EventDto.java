package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class EventDto {

    @Data
    @Builder
    public static class EventsResponse {
        private List<String> dateSegments;
        private List<String> categories;
        private List<EventItem> items;
    }

    @Data
    @Builder
    public static class EventItem {
        private Long eventId;
        private String timeText;
        private String title;
        private String statusText;
        private List<String> tags;
        private Integer importanceStars;
        private String countdownText;
        private List<String> relatedAssets;
        private boolean alertEnabled;
    }

    @Data
    @Builder
    public static class EventAlertResponse {
        private Long eventId;
        private boolean enabled;
    }

    @Data
    public static class UpdateEventAlertRequest {
        private boolean enabled;

        public UpdateEventAlertRequest(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}
