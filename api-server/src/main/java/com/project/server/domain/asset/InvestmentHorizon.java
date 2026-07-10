package com.project.server.domain.asset;

public enum InvestmentHorizon {
    UNDER_1Y(12),
    ONE_TO_THREE_YEARS(36),
    THREE_TO_FIVE_YEARS(60),
    OVER_FIVE_YEARS(96);

    private final int representativeMonths;

    InvestmentHorizon(int representativeMonths) {
        this.representativeMonths = representativeMonths;
    }

    public int representativeMonths() {
        return representativeMonths;
    }
}
