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
