package com.project.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

public class UserAssetDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyBriefingResponse {
        private BigDecimal assetTotal;
        private BigDecimal dailyChangePct;
        private BigDecimal drawdownPct;
        private Integer maxDrawdownTolerance;
        private BigDecimal ratio;
        private String status;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingItem {
        private String ticker;
        private String name;
        private BigDecimal weightPct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoldingsResponse {
        private java.util.List<HoldingItem> holdings;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateGoalRequest {
        private String financialGoal;
        private BigDecimal targetAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalResponse {
        private String goalLabel;
        private BigDecimal targetAmount;
        private BigDecimal goalStartAmount;
        private java.time.LocalDate goalStartDate;
        private java.time.OffsetDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalProgressResponse {
        private String goalLabel;
        private Integer progressPct;
        private String scheduleStatus;
        private String scheduleNote;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionHeartbeatRequest {
        private String deviceId;
        private Boolean appOpen;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionHeartbeatResponse {
        private String syncStatus;
        private String syncReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InvestmentProfileResponse {
        private String investmentHorizon;
        private Integer maxDrawdownTolerance;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateInvestmentProfileRequest {
        private String investmentHorizon;
        private Integer maxDrawdownTolerance;
    }
}
