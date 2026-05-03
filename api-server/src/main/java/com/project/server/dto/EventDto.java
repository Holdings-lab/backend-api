package com.project.server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    @Builder
    public static class RelatedPoliciesResponse {
        private Long userId;
        private String assetName;
        private List<RelatedPolicyItem> policies;
    }

    @Data
    @Builder
    public static class RelatedPolicyItem {
        private Long eventId;
        private String title;
        private String category;
        private String date;
        private String direction;
        private Integer volatilityScore;
        private Integer relevanceScore;
        private String reason;
    }

    @Data
    @Builder
    public static class RelatedAssetsResponse {
        private Long userId;
        private Long eventId;
        private String policyTitle;
        private List<RelatedAssetItem> assets;
    }

    @Data
    @Builder
    public static class RelatedAssetItem {
        private String assetName;
        private String direction;
        private Integer volatilityScore;
        private Integer relevanceScore;
        private String reason;
    }

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = false)
    public static class UpdateEventAlertRequest {
        @JsonProperty(value = "enabled", required = true)
        private boolean enabled;
    }
}
