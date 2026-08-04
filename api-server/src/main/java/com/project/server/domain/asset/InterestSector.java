package com.project.server.domain.asset;

import java.util.Arrays;

public enum InterestSector {
    SEMICONDUCTOR("반도체"),
    AI_PLATFORM("AI · 플랫폼"),
    SECONDARY_BATTERY("2차전지"),
    GREEN_ENERGY("친환경 · 에너지"),
    FINANCE("금융"),
    HEALTHCARE_BIO("헬스케어 · 바이오"),
    CONSUMER_RETAIL("소비재 · 리테일"),
    REAL_ESTATE_REIT("부동산 · 리츠");

    private final String label;

    InterestSector(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static InterestSector fromString(String value) {
        return Arrays.stream(values())
                .filter(sector -> sector.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown interest sector: " + value));
    }
}
