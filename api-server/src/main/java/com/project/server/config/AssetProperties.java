package com.project.server.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "asset")
public class AssetProperties {

    private final Session session = new Session();
    private final Status status = new Status();
    private final Batch batch = new Batch();

    @Getter
    @Setter
    public static class Session {
        private int heartbeatTtlMinutes = 15;
        private int syncThrottleMinutes = 5;
        private int refreshLockSeconds = 10;
    }

    @Getter
    @Setter
    public static class Status {
        private BigDecimal watchRatio = new BigDecimal("0.5");
        private BigDecimal alertRatio = BigDecimal.ONE;
    }

    @Getter
    @Setter
    public static class Batch {
        private String previousDaySnapshotCron = "0 30 8 * * *";
        private String allTimeHighScanCron = "0 30 6 * * 2-6";
    }
}
