package com.project.server.service.auth;

import com.project.server.domain.UserNotificationSettingEntity;
import com.project.server.exception.ApiException;
import com.project.server.repository.UserNotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final String DEFAULT_BRIEFING_TIME = "09:00";
    private static final Pattern BRIEFING_TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final UserNotificationSettingRepository notificationSettingRepository;

    @Transactional(readOnly = true)
    public UserNotificationSettingEntity getSettings(Long userId) {
        return notificationSettingRepository.findByUserId(userId)
                .orElseGet(this::defaultSettings);
    }

    @Transactional
    public UserNotificationSettingEntity upsertNotificationSettings(
            Long userId,
            Boolean policyChangeAlert,
            String briefingTime) {
        if (policyChangeAlert == null && briefingTime == null) {
            throw ApiException.badRequest(
                    "policyChangeAlert 또는 briefingTime 중 하나 이상 필요합니다.",
                    "INVALID_NOTIFICATION_SETTINGS");
        }

        String normalizedBriefingTime = null;
        if (briefingTime != null) {
            normalizedBriefingTime = normalizeBriefingTime(briefingTime);
        }

        UserNotificationSettingEntity current = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> defaultSettingsForUser(userId));

        if (policyChangeAlert != null) {
            current.setPolicyChangeAlert(policyChangeAlert);
            current.setImportantEventBriefing(policyChangeAlert);
        }
        if (normalizedBriefingTime != null) {
            current.setBriefingTime(normalizedBriefingTime);
        }

        return notificationSettingRepository.save(current);
    }

    public String normalizeBriefingTime(String briefingTime) {
        if (briefingTime == null || briefingTime.isBlank()) {
            throw ApiException.badRequest("briefingTime은 필수입니다.", "INVALID_BRIEFING_TIME");
        }
        String trimmed = briefingTime.trim();
        if (!BRIEFING_TIME_PATTERN.matcher(trimmed).matches()) {
            throw ApiException.badRequest(
                    "briefingTime은 HH:mm 형식이어야 합니다.",
                    "INVALID_BRIEFING_TIME");
        }
        return trimmed;
    }

    private UserNotificationSettingEntity defaultSettings() {
        return UserNotificationSettingEntity.builder()
                .before30m(true)
                .importantEventBriefing(true)
                .learningReminder(true)
                .policyChangeAlert(true)
                .briefingTime(DEFAULT_BRIEFING_TIME)
                .build();
    }

    private UserNotificationSettingEntity defaultSettingsForUser(Long userId) {
        UserNotificationSettingEntity entity = defaultSettings();
        entity.setUserId(userId);
        return entity;
    }
}
