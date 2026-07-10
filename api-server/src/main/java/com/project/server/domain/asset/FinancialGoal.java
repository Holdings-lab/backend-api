package com.project.server.domain.asset;

import java.util.Arrays;

public enum FinancialGoal {
    RETIREMENT("은퇴 자금 목표");

    private final String label;

    FinancialGoal(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static FinancialGoal fromString(String value) {
        return Arrays.stream(values())
                .filter(goal -> goal.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown financial goal: " + value));
    }
}
