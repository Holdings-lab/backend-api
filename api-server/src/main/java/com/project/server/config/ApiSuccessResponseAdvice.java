package com.project.server.config;

import com.project.server.dto.ApiResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

@RestControllerAdvice(basePackages = "com.project.server.controller")
public class ApiSuccessResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final String SUCCESS_CODE = "SUCCESS-200";
    private static final String SUCCESS_MESSAGE = "요청에 성공했습니다.";

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return !StringHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof ApiResponse<?>) {
            return body;
        }

        HttpStatus status = HttpStatus.OK;
        if (response instanceof ServletServerHttpResponse servletResponse) {
            status = HttpStatus.valueOf(servletResponse.getServletResponse().getStatus());
        }
        if (!status.is2xxSuccessful()) {
            return body;
        }

        Object result = body == null ? Map.of() : body;
        return ApiResponse.success(SUCCESS_CODE, SUCCESS_MESSAGE, result);
    }
}
