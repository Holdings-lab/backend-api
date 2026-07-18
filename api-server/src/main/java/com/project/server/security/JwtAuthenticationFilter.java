package com.project.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.dto.ApiResponse;
import com.project.server.exception.ApiException;
import com.project.server.service.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청의 Authorization: Bearer 헤더가 있으면 액세스 토큰을 검증하고,
 * 사용자 ID를 요청 속성에 저장한다. 토큰이 유효하지 않으면 401 을 반환한다.
 * 헤더가 없으면 통과시켜(공개 엔드포인트 및 경로 기반 API 호환) 후속 처리에 위임한다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_USER_ID_ATTRIBUTE = "authUserId";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                Long userId = jwtTokenProvider.validateAndGetUserId(token);
                request.setAttribute(AUTH_USER_ID_ATTRIBUTE, userId);
            } catch (ApiException ex) {
                writeError(response, ex);
                return;
            }
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
