package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class ActionDto {

    @Data
    @Builder
    public static class ActionResponse {
        private String action;
        private String status;
        private Long userId;
        private Long eventId;
        private String featuredTitle;
        private String featuredSummary;
        private String countdownText;
        private List<String> tags;
        private Integer policyFeedCardCount;
    }

    @Data
    @Builder
    public static class TrainRegressionResponse {
        private String scriptPath;
        private String command;
        private int exitCode;
        private long elapsedMs;
        private String stdoutTail;
        private String stderrTail;
        private List<String> fallbackPaths;
    }
}
