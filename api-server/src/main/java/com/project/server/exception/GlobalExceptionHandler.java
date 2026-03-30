package com.project.server.exception;

import com.project.server.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex) {
        log.warn("API 예외 발생: code={}, message={}", ex.getErrorCode(), ex.getMessage());
        return buildErrorResponse(ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("요청 검증 실패: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("요청 본문 파싱 실패: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("데이터 무결성 오류: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleUnexpectedException(Exception ex) {
        log.error("예상치 못한 서버 오류", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiResponse<Object>> buildErrorResponse(HttpStatus status) {
        return ResponseEntity.status(status).body(
                ApiResponse.error(resolveCodeByStatus(status), resolveMessageByStatus(status))
        );
    }

    private String resolveCodeByStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "COMMON-002";
            case UNAUTHORIZED -> "COMMON-401";
            case NOT_FOUND -> "COMMON-404";
            case INTERNAL_SERVER_ERROR -> "COMMON-500";
            default -> "ERROR-" + status.value();
        };
    }

    private String resolveMessageByStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "요청 파라미터가 올바르지 않습니다.";
            case UNAUTHORIZED -> "인증 정보가 유효하지 않습니다.";
            case NOT_FOUND -> "존재하지 않는 리소스입니다.";
            case INTERNAL_SERVER_ERROR -> "서버 오류가 발생했습니다.";
            default -> "요청 처리 중 오류가 발생했습니다.";
        };
    }
}
