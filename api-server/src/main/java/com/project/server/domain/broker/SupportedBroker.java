package com.project.server.domain.broker;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 온보딩/연동에서 노출하는 증권사 목록.
 * code는 브로커 코드(KIS 등)와 동일해야 한다.
 */
public enum SupportedBroker {
    KIS("한국투자증권", true),
    MIRAE("미래에셋", false),
    NH("NH투자증권", false),
    KB("KB증권", false),
    SAMSUNG("삼성증권", false),
    KIWOOM("키움증권", false);

    private final String displayName;
    private final boolean available;

    SupportedBroker(String displayName, boolean available) {
        this.displayName = displayName;
        this.available = available;
    }

    public String code() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public boolean available() {
        return available;
    }

    public static Optional<SupportedBroker> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(broker -> broker.name().equalsIgnoreCase(code.trim()))
                .findFirst();
    }

    public static SupportedBroker requireAvailable(String code) {
        SupportedBroker broker = fromCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown broker: " + code));
        if (!broker.available) {
            throw new IllegalStateException("Broker not available: " + code);
        }
        return broker;
    }

    public static String normalizeCode(String code) {
        return code == null ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
