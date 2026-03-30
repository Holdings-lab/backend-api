package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ApiErrorResponse {
    private String errorCode;
    private String message;
    private List<FieldErrorItem> fieldErrors;
    private LocalDateTime timestamp;

    @Data
    @Builder
    public static class FieldErrorItem {
        private String field;
        private String reason;
    }
}
