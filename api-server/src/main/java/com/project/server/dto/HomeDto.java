package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class HomeDto {

    @Data
    @Builder
    public static class HomeResponse {
        private UserGreeting userGreeting;
        private FeaturedEvent featuredEvent;
        private LearningCard learningCard;
        private List<WatchAssetImpact> watchAssetImpacts;
        private List<InsightCard> threeInsights;
        private ReasonPanel reasonPanel;
        private PredictionReview predictionReview;
        private String disclaimerText;
    }

    @Data
    @Builder
    public static class UserGreeting {
        private String displayName;
        private String headline;
        private String subtext;
    }

    @Data
    @Builder
    public static class FeaturedEvent {
        private String label;
        private String dDayText;
        private String title;
        private List<String> tags;
        private String summary;
        private String metaText;
    }

    @Data
    @Builder
    public static class LearningCard {
        private String label;
        private String title;
        private int progressPercent;
        private String ctaText;
    }

    @Data
    @Builder
    public static class WatchAssetImpact {
        private String assetName;
        private String signalText;
    }

    @Data
    @Builder
    public static class InsightCard {
        private String title;
        private String subtitle;
        private String detail;
    }

    @Data
    @Builder
    public static class ReasonPanel {
        private String title;
        private String sourceText;
        private List<ReasonItem> items;
        private String ctaText;
    }

    @Data
    @Builder
    public static class ReasonItem {
        private int rank;
        private String label;
        private int score;
    }

    @Data
    @Builder
    public static class PredictionReview {
        private String title;
        private String eventName;
        private String summary;
        private String ctaText;
    }
}
