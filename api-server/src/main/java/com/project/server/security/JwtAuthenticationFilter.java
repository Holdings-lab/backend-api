package com.project.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.config.AdminProperties;
import com.project.server.dto.ApiResponse;
import com.project.server.exception.ApiException;
import com.project.server.repository.UserJpaRepository;
import com.project.server.service.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authorization: Bearer 액세스 토큰을 검증하고 authUserId 를 요청 속성에 저장한다.
 * 보호 경로에서는 토큰이 필수이며, 없으면 401 을 반환한다.
 * Bearer 값이 관리자 API 키이면 X-User-Id 로 지정한 사용자를 가장한다.
 * /api/auth/**, webhook, feeds/insights/brokers, /admin/**, health 등은 공개(또는 별도 인증).
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_USER_ID_ATTRIBUTE = "authUserId";
    public static final String USER_ID_HEADER = "X-User-Id";
    private static final String BEARER_PREFIX = "Bearer ";

    /** JWT 필수인 사용자/도메인 API prefix */
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/me",
            "/api/home",
            "/api/events",
            "/api/accounts",
            "/api/portfolio",
            "/api/holdings",
            "/api/goal",
            "/api/goal-progress",
            "/api/daily-briefing",
            "/api/session",
            "/api/investment-profile",
            "/api/newsroom",
            "/api/onboarding",
            "/api/watch-assets",
            "/api/interest-sectors",
            "/api/ml"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final AdminProperties adminProperties;
    private final UserJpaRepository userJpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = normalizePath(request.getRequestURI());
        boolean authRequired = requiresAuthentication(path);
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            try {
                if (adminProperties.matches(token)) {
                    applyAdminImpersonation(request, path, authRequired);
                } else {
                    Long userId = jwtTokenProvider.validateAndGetUserId(token);
                    request.setAttribute(AUTH_USER_ID_ATTRIBUTE, userId);
                }
            } catch (ApiException ex) {
                // /admin/token 은 admin 키·무효 토큰도 컨트롤러에서 판별
                if (!isTokenInspectPath(path)) {
                    writeError(response, ex);
                    return;
                }
            }
        } else if (authRequired) {
            writeError(response, ApiException.unauthorized(
                    "인증이 필요합니다. 액세스 토큰을 포함해주세요.", "AUTH_REQUIRED"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 관리자 API 키로 들어온 요청을 X-User-Id 대상 사용자로 가장한다.
     * 보호 경로에서는 X-User-Id 가 필수이며, HTTP 메서드는 제한하지 않는다.
     */
    private void applyAdminImpersonation(HttpServletRequest request, String path, boolean authRequired) {
        String rawUserId = request.getHeader(USER_ID_HEADER);
        if (rawUserId == null || rawUserId.isBlank()) {
            if (!authRequired) {
                return;
            }
            throw ApiException.badRequest(
                    "관리자 토큰으로 사용자 API를 호출하려면 X-User-Id 헤더가 필요합니다.",
                    "ADMIN_USER_ID_REQUIRED");
        }

        long userId;
        try {
            userId = Long.parseLong(rawUserId.trim());
        } catch (NumberFormatException ex) {
            throw ApiException.badRequest("X-User-Id 형식이 올바르지 않습니다.", "ADMIN_USER_ID_INVALID");
        }
        if (userId <= 0 || !userJpaRepository.existsById(userId)) {
            throw ApiException.notFound("사용자를 찾을 수 없습니다.", "USER_NOT_FOUND");
        }

        request.setAttribute(AUTH_USER_ID_ATTRIBUTE, userId);
        log.info("[admin-impersonate] path={} userId={}", path, userId);
    }

    private static boolean isTokenInspectPath(String path) {
        return path != null && (path.equals("/admin/token") || path.startsWith("/admin/token/"));
    }

    private static boolean requiresAuthentication(String path) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/api/auth")
                || path.startsWith("/api/internal/webhooks")
                || path.startsWith("/admin")
                || path.equals("/api/health")
                || path.startsWith("/actuator")
                || path.startsWith("/api/feeds")
                || path.startsWith("/api/insights")
                || path.startsWith("/api/brokers")) {
            return false;
        }
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return uri;
        }
        int query = uri.indexOf('?');
        return query >= 0 ? uri.substring(0, query) : uri;
    }

    private void writeError(HttpServletResponse response, ApiException ex) throws IOException {
        response.setStatus(ex.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }
}
