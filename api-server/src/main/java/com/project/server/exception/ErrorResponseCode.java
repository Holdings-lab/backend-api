package com.project.server.exception;

import lombok.Getter;

@Getter
public enum ErrorResponseCode {
    DEFAULT_FAILURE("FAIL-001", "요청에 실패했습니다."),
    INVALID_REQUEST_BODY("FAIL-002", "요청 형식이 올바르지 않거나 잘못된 필드명이 포함되어 있습니다."),
    NOT_FOUND("FAIL-003", "존재하지 않는 API 경로입니다.");

    private final String code;
    private final String message;

    ErrorResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
