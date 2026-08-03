package com.project.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class NewsroomDto {

    public enum BriefingType {
        Hero,
        Compact,
        Quiet
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TabResponse {
        private Header header;
        private Section section;
        private List<HoldingBriefing> holdings;
        private TabFooter footer;
        private EmptyState emptyState;
        private ErrorState errorState;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        private String title;
        private String asOfAt;
        private String subtitle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Section {
        private String title;
        private int itemCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingBriefing {
        private String ticker;
        private String name;
        private BigDecimal weightPct;
        private BriefingType briefingType;
        private BigDecimal dailyChangePct;
        private BigDecimal totalAssetImpactPct;
        private String headline;
        private String summary;
        private String detailPath;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TabFooter {
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmptyState {
        private String code;
        private String title;
        private String description;
        private String buttonLabel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorState {
        private String title;
        private String summary;
        private String buttonLabel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailResponse {
        private StockMeta stock;
        private String headline;
        private String imageUrl;
        private String aiJudgement;
        private DetailSummary summary;
        private List<SourceItem> sources;
        private DetailFooter footer;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockMeta {
        private String ticker;
        private String name;
        private BigDecimal dailyChangePct;
        private BigDecimal weightPct;
        private BigDecimal totalAssetImpactPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailSummary {
        private String body;
        private List<String> findings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceItem {
        private String newsId;
        private String title;
        private String publisher;
        private String publishedAt;
        private String url;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailFooter {
        private String asOfAt;
        private String aiNotice;
    }
}
