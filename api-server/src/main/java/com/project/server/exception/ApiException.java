package com.project.server.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static ApiException badRequest(String message, String errorCode) {
        return new ApiException(HttpStatus.BAD_REQUEST, errorCode, message);
    }

    public static ApiException conflict(String message, String errorCode) {
        return new ApiException(HttpStatus.CONFLICT, errorCode, message);
    }

    public static ApiException notFound(String message, String errorCode) {
        return new ApiException(HttpStatus.NOT_FOUND, errorCode, message);
    }

    public static ApiException unauthorized(String message, String errorCode) {
        return new ApiException(HttpStatus.UNAUTHORIZED, errorCode, message);
    }
}
