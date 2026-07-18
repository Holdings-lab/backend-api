package com.project.server.service.auth;

import com.project.server.config.JwtProperties;
import com.project.server.domain.RefreshTokenEntity;
import com.project.server.exception.ApiException;
import com.project.server.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * 리프레시 토큰의 발급/회전(rotation)/폐기를 담당한다.
 * 토큰 원문은 클라이언트에만 전달하고, 서버에는 SHA-256 해시만 저장한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 48;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public String issue(Long userId) {
        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusDays(jwtProperties.getRefreshTokenExpirationDays());

        refreshTokenRepository.save(RefreshTokenEntity.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .expiresAt(expiresAt)
                .revoked(false)
                .build());

        return rawToken;
    }

    /**
     * 기존 리프레시 토큰을 검증 후 폐기하고 새 토큰을 발급한다(회전).
     */
    @Transactional
    public Rotation rotate(String rawRefreshToken) {
        RefreshTokenEntity stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized(
                        "유효하지 않은 리프레시 토큰입니다.", "AUTH_REFRESH_TOKEN_INVALID"));

        if (stored.isRevoked()) {
            throw ApiException.unauthorized("이미 사용되었거나 폐기된 리프레시 토큰입니다.",
                    "AUTH_REFRESH_TOKEN_REVOKED");
        }

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.unauthorized("리프레시 토큰이 만료되었습니다. 다시 로그인해주세요.",
                    "AUTH_REFRESH_TOKEN_EXPIRED");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String newRawToken = issue(stored.getUserId());
        return new Rotation(stored.getUserId(), newRawToken);
    }

    /**
     * 로그아웃 등에서 리프레시 토큰을 폐기한다. 존재하지 않아도 조용히 성공 처리한다(멱등).
     */
    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", ex);
        }
    }

    public record Rotation(Long userId, String refreshToken) {
    }
}
