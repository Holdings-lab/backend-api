package com.project.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

public class OnboardingDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateProfileRequest {
        private String financialGoal;
        private BigDecimal targetAmount;
        private String investmentHorizon;
        private String riskTolerance;
        private String investmentStyle;
        private List<String> interests;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileData {
        private String financialGoal;
        private BigDecimal targetAmount;
        private String investmentHorizon;
        private String riskTolerance;
        private String investmentStyle;
        private List<String> interests;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusResponse {
        private int lastCompletedStep;
        @JsonProperty("isCompleted")
        private boolean completed;
        private boolean accountLinked;
        private boolean accountSkipped;
        private ProfileData savedData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerOption {
        private String code;
        private String name;
        @JsonProperty("available")
        private boolean available;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerListResponse {
        private List<BrokerOption> brokers;
    }
}
