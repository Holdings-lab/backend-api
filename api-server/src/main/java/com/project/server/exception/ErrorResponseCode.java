package com.project.server.exception;

import lombok.Getter;

@Getter
public enum ErrorResponseCode {
    DEFAULT_FAILURE("FAIL-001", "요청에 실패했습니다.");

    private final String code;
    private final String message;

    ErrorResponseCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
