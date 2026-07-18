package com.project.server.service.security;

import com.project.server.config.JwtProperties;
import com.project.server.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 액세스 토큰 발급/검증을 담당한다.
 * 리프레시 토큰은 별도로 DB에 저장되는 불투명(opaque) 토큰으로 관리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_TYPE = "type";
    private static final String TOKEN_TYPE_ACCESS = "access";

    private final JwtProperties jwtProperties;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.isBlank()) {
            this.signingKey = Jwts.SIG.HS256.key().build();
            log.warn("jwt.secret 이 설정되지 않아 임시 서명키를 생성했습니다. "
                    + "서버 재시작 시 발급된 모든 토큰이 무효화됩니다. "
                    + "프로덕션에서는 JWT_SECRET 환경변수를 반드시 설정하세요.");
            return;
        }

        byte[] keyBytes = decodeSecret(secret);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "jwt.secret 은 HS256 서명을 위해 최소 32바이트(256-bit) 이상이어야 합니다. 현재 "
                            + keyBytes.length + "바이트");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private byte[] decodeSecret(String secret) {
        try {
            return Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException ex) {
            return secret.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String generateAccessToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + getAccessTokenExpirationMillis());
        return Jwts.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(String.valueOf(userId))
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 액세스 토큰을 검증하고 사용자 ID(subject)를 반환한다.
     * 만료/서명오류/형식오류 시 401 ApiException 을 던진다.
     */
    public Long validateAndGetUserId(String token) {
        Claims claims = parseClaims(token);
        if (!TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw ApiException.unauthorized("액세스 토큰이 아닙니다.", "AUTH_TOKEN_INVALID");
        }
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw ApiException.unauthorized("토큰 정보가 올바르지 않습니다.", "AUTH_TOKEN_INVALID");
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(jwtProperties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw ApiException.unauthorized("액세스 토큰이 만료되었습니다.", "AUTH_TOKEN_EXPIRED");
        } catch (JwtException | IllegalArgumentException ex) {
            throw ApiException.unauthorized("유효하지 않은 토큰입니다.", "AUTH_TOKEN_INVALID");
        }
    }

    public long getAccessTokenExpirationMillis() {
        return jwtProperties.getAccessTokenExpirationMinutes() * 60_000L;
    }

    public long getAccessTokenExpirationSeconds() {
        return jwtProperties.getAccessTokenExpirationMinutes() * 60L;
    }
}
