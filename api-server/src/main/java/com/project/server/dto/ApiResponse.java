package com.project.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    @JsonProperty("isSuccess")
    private boolean isSuccess;
    private String code;
    private String message;
    private T result;

    public static <T> ApiResponse<T> success(String code, String message, T result) {
        return ApiResponse.<T>builder()
                .isSuccess(true)
                .code(code)
                .message(message)
                .result(result)
                .build();
    }

    public static ApiResponse<Object> error(String code, String message) {
        return ApiResponse.builder()
                .isSuccess(false)
                .code(code)
                .message(message)
                .build();
    }
}