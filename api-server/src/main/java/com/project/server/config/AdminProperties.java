package com.project.server.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /**
     * /admin API 보호용 공유 시크릿. 비어 있으면 /admin 요청은 전부 거부된다.
     */
    private String apiKey = "";

    @PostConstruct
    void warnIfApiKeyMissing() {
        if (!StringUtils.hasText(apiKey)) {
            log.warn("admin.api-key(ADMIN_API_KEY)가 설정되지 않았습니다. /admin 엔드포인트는 모두 401로 거부됩니다.");
        }
    }

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * 전달된 값이 설정된 관리자 API 키와 일치하는지 비교한다.
     * 키가 미설정이거나 값이 비어 있으면 false.
     */
    public boolean matches(String provided) {
        if (!hasApiKey() || !StringUtils.hasText(provided)) {
            return false;
        }
        return MessageDigest.isEqual(
                apiKey.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
