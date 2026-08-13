package com.project.server.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
}
