package com.project.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class PolicyFeedDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyFeedRequest {
        private Integer limit;
        private String category;
        private Long userId;
        private String dateFrom;
        private String dateTo;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PolicyFeedResponse {
        private String feedType;
        private String generatedAt;
        private Source source;
        private Summary summary;
        private Model model;
        private Filters filters;
        private List<Card> cards;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Source {
        private String dataset;
        private String modelTarget;
        private String modelVersion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private int totalCount;
        private int positiveCount;
        private int negativeCount;
        private int neutralCount;
        private String overallSentiment;
        private Double overallSentimentScore;
        private String latestDate;
        private List<CategoryCount> topCategories;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryCount {
        private String category;
        private int count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Model {
        private String targetTicker;
        private Integer bestHorizonDays;
        private List<String> bestFeatures;
        private Metrics metrics;
        private List<ThresholdPerformance> thresholdPerformance;
        private List<TopFeatureImportance> topFeatureImportance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metrics {
        private Double mae;
        private Double rmse;
        private Double r2;
        private Double directionAccuracy;
        private Double mape;
        private Double baselineMae;
        private Double baselineRmse;
        private Double baselineMape;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThresholdPerformance {
        private Double threshold;
        private Double finalReturn;
        private Double marketReturn;
        private Double excessReturn;
        private Integer tradeCount;
        private Double winRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopFeatureImportance {
        private Integer rank;
        private String feature;
        private Double importance;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Filters {
        private List<String> categories;
        private List<String> docTypes;
        private DateRange dateRange;
        private SentimentRange sentimentRange;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateRange {
        private String from;
        private String to;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SentimentRange {
        private Double min;
        private Double max;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Card {
        private String id;
        private String date;
        private String category;
        private String docType;
        private String title;
        private String bodySummary;
        private String bodyExcerpt;
        private String link;
        private Integer bodyOriginalLength;
        private Integer bodyNChunks;
        private List<String> tags;
        private Temporal temporal;
        private Sentiment sentiment;
        private ModelSignal modelSignal;
        private Impact impact;
        private Features features;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Temporal {
        private String dayOfWeek;
        private Integer month;
        private Boolean isWeekend;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sentiment {
        private Double titlePositiveProb;
        private Double titleNegativeProb;
        private Double titleNeutralProb;
        private Double titleSentimentScore;
        private Double bodyPositiveProb;
        private Double bodyNegativeProb;
        private Double bodyNeutralProb;
        private Double bodySentimentScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelSignal {
        private Integer horizonDays;
        private Double predictedLogReturn;
        private Double predictedReturnPct;
        private Double predictedFuturePrice;
        private String signal;
        private Integer signalStrength;
        private Double thresholdUsed;
        private Double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Impact {
        private String label;
        private Integer score;
        private String reason;
        private List<String> targetAssets;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Features {
        private List<String> matchedFeatures;
        private List<String> featureDrivers;
    }
}