package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

public class ActionDto {

    @Data
    @Builder
    public static class ActionResponse {
        private String message;
    }
}
