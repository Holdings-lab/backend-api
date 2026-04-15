package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class HomeBriefingDto {

    @Data
    @Builder
    public static class BriefingResponse {
        private HomeHeader homeHeader;
        private FeaturedSignalCard featuredCard;
        private PortfolioCard portfolioCard;
        private List<SecondarySignalItem> secondarySignals;
        private QuickInterpretation quickInterpretation;
        private DetailTabs detailTabs;
        private CheckpointTab checkpointTab;
        private String disclaimer;
    }

    @Data
    @Builder
    public static class HomeHeader {
        private String greeting;
        private String userName;
        private String profileInitial;
    }

    @Data
    @Builder
    public static class FeaturedSignalCard {
        private String signalTitle;
        private int myAssetExposurePercent;
        private String recommendedAction;
        private String judgement;
        private int upsideProbability;
        private int downsideProbability;
        private int volatility;
        private int confidence;
        private String coreReason;
    }

    @Data
    @Builder
    public static class PortfolioCard {
        private String totalAsset;
        private String returnRate;
        private String currentRiskLabel;
        private String currentRiskSummary;
        private List<ThemeExposureBar> themeExposureBars;
    }

    @Data
    @Builder
    public static class ThemeExposureBar {
        private String theme;
        private int exposurePercent;
    }

    @Data
    @Builder
    public static class SecondarySignalItem {
        private String title;
        private String shortJudgement;
        private int exposurePercent;
        private String oneLineReason;
    }

    @Data
    @Builder
    public static class QuickInterpretation {
        private JudgementBadge judgementBadge;
        private String myAssetImpact;
        private String coreReason;
        private List<KeyNumberItem> keyNumbers;
        private String revisitTime;
        private String weakenCondition;
        private String tip;
    }

    @Data
    @Builder
    public static class JudgementBadge {
        private String text;
        private String color;
        private String displayType;
    }

    @Data
    @Builder
    public static class KeyNumberItem {
        private String label;
        private String baseline;
        private String description;
    }

    @Data
    @Builder
    public static class DetailTabs {
        private SummaryTab summaryTab;
        private EvidenceTab evidenceTab;
    }

    @Data
    @Builder
    public static class SummaryTab {
        private String judgement;
        private int exposurePercent;
        private String oneLineSummary;
    }

    @Data
    @Builder
    public static class EvidenceTab {
        private List<ImpactPathItem> impactPaths;
        private List<String> coreEvidences;
        private List<String> counterEvidences;
        private List<String> invalidationConditions;
    }

    @Data
    @Builder
    public static class ImpactPathItem {
        private String icon;
        private String title;
        private String description;
    }

    @Data
    @Builder
    public static class CheckpointTab {
        private List<CheckpointItem> policyCheckpoints;
        private List<CheckpointItem> marketCheckpoints;
        private String revisitAlert;
        private ReflectionStatus reflectionStatus;
    }

    @Data
    @Builder
    public static class CheckpointItem {
        private String title;
        private String threshold;
        private String reason;
    }

    @Data
    @Builder
    public static class ReflectionStatus {
        private String updatedAt;
        private List<String> sources;
        private String reviewStatus;
    }
}
