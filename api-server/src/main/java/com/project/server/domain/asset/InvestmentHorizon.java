package com.project.server.domain.asset;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

public enum InvestmentHorizon {
    UNDER_1Y(12),
    Y1_3(36),
    Y3_5(60),
    OVER_5Y(96);

    private static final Map<String, InvestmentHorizon> ALIASES = Map.ofEntries(
            Map.entry("UNDER_1Y", UNDER_1Y),
            Map.entry("UNDER_1_YEAR", UNDER_1Y),
            Map.entry("Y1_3", Y1_3),
            Map.entry("YEAR_1_TO_3", Y1_3),
            Map.entry("ONE_TO_THREE_YEARS", Y1_3),
            Map.entry("Y3_5", Y3_5),
            Map.entry("YEAR_3_TO_5", Y3_5),
            Map.entry("THREE_TO_FIVE_YEARS", Y3_5),
            Map.entry("OVER_5Y", OVER_5Y),
            Map.entry("OVER_5_YEARS", OVER_5Y),
            Map.entry("OVER_FIVE_YEARS", OVER_5Y)
    );

    private final int representativeMonths;

    InvestmentHorizon(int representativeMonths) {
        this.representativeMonths = representativeMonths;
    }

    public int representativeMonths() {
        return representativeMonths;
    }

    public static InvestmentHorizon fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Unknown investment horizon: " + value);
        }
        InvestmentHorizon mapped = ALIASES.get(value.trim().toUpperCase(Locale.ROOT));
        if (mapped != null) {
            return mapped;
        }
        return Arrays.stream(values())
                .filter(horizon -> horizon.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown investment horizon: " + value));
    }
}
