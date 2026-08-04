package com.project.server.domain.asset;

import java.util.Arrays;

public enum FinancialGoal {
    RETIREMENT("은퇴 자금 마련"),
    SEED_MONEY("목돈(종잣돈) 모으기"),
    SPARE_FUND("여유자금 굴리기"),
    HOUSE("내 집 마련");

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
