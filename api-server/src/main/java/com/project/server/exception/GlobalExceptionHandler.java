package com.project.server.exception;

import com.project.server.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Object>> handleApiException(ApiException ex) {
        log.warn("API 예외 발생: code={}, message={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus()).body(
                ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("파라미터 '%s'은(는) %s 타입이어야 합니다.",
                ex.getName(), ex.getRequiredType().getSimpleName());
        log.warn("파라미터 타입 불일치: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.error("INVALID_PARAMETER_TYPE", message));
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ApiResponse<Object>> handleRequestBinding(ServletRequestBindingException ex) {
        log.warn("요청 바인딩 실패: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.error("INVALID_REQUEST_BINDING", "요청 파라미터 바인딩에 실패했습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("요청 검증 실패: {}", errorMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.error(ErrorResponseCode.INVALID_REQUEST_BODY.getCode(), errorMessage));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("요청 본문 파싱 실패: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiResponse.error(ErrorResponseCode.INVALID_REQUEST_BODY.getCode(),
                        ErrorResponseCode.INVALID_REQUEST_BODY.getMessage()));
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
                ApiResponse.error(errorCode.getCode(), errorCode.getMessage()));
    }

    private ErrorResponseCode resolveErrorCode(HttpStatus status) {
        // 현재는 단일 실패 응답을 사용하고, 상태별 매핑은 이 메서드에서 확장한다.
        return ErrorResponseCode.DEFAULT_FAILURE;
    }
}
