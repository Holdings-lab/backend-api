package com.project.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * HS256 서명키. 비어 있으면 부팅 시 임시 키를 생성한다(개발용).
     */
    private String secret = "";

    private String issuer = "holdings-lab";

    private long accessTokenExpirationMinutes = 30;

    private long refreshTokenExpirationDays = 14;
}
