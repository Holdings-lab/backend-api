package com.project.server.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class AppTime {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private AppTime() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(KST);
    }

    public static LocalDate today() {
        return LocalDate.now(KST);
    }
}
