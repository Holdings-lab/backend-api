package com.project.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.config.AdminProperties;
import com.project.server.dto.ApiResponse;
import com.project.server.exception.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * /admin/* 요청에 X-Admin-Key 헤더를 검증한다.
 * 키가 미설정이거나 불일치하면 401을 반환한다.
 */
@RequiredArgsConstructor
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    public static final String ADMIN_KEY_HEADER = "X-Admin-Key";

    private final AdminProperties adminProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/admin")) {
            return true;
        }
        // 토큰 판별 엔드포인트는 X-Admin-Key 없이 Authorization 만으로도 호출 가능
        return path.equals("/admin/token") || path.startsWith("/admin/token/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!adminProperties.hasApiKey()) {
            writeError(response, ApiException.unauthorized(
                    "관리자 API 키가 서버에 설정되지 않았습니다.", "ADMIN_KEY_NOT_CONFIGURED"));
            return;
        }

        String provided = request.getHeader(ADMIN_KEY_HEADER);
        if (!adminProperties.matches(provided)) {
            writeError(response, ApiException.unauthorized(
                    "유효한 관리자 API 키가 필요합니다.", "ADMIN_UNAUTHORIZED"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, ApiException ex) throws IOException {
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }
}
