package com.project.server.domain.asset;

import java.util.Arrays;

public enum InvestmentStyle {
    CONSERVATIVE,
    MODERATE,
    AGGRESSIVE;

    public static InvestmentStyle fromString(String value) {
        return Arrays.stream(values())
                .filter(style -> style.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown investment style: " + value));
    }
}
