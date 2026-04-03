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
        ErrorResponseCode errorCode = resolveErrorCode(status);
        return ResponseEntity.status(status).body(
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage())
        );
    }

    private ErrorResponseCode resolveErrorCode(HttpStatus status) {
        // 현재는 단일 실패 응답을 사용하고, 상태별 매핑은 이 메서드에서 확장한다.
        return ErrorResponseCode.DEFAULT_FAILURE;
    }
}
