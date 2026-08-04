package com.project.server.domain.asset;

import java.util.Arrays;

public enum RiskToleranceBand {
    WITHIN_10(10),
    WITHIN_20(20),
    OVER_30(30);

    private final int percent;

    RiskToleranceBand(int percent) {
        this.percent = percent;
    }

    public int percent() {
        return percent;
    }

    public static RiskToleranceBand fromString(String value) {
        return Arrays.stream(values())
                .filter(band -> band.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown risk tolerance: " + value));
    }

    public static RiskToleranceBand fromPercent(Integer percent) {
        if (percent == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(band -> band.percent == percent)
                .findFirst()
                .orElse(null);
    }
}
