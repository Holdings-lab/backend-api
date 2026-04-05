package com.project.server.service.auth;

import com.project.server.domain.UserNotificationSettingEntity;
import com.project.server.dto.UserPreferenceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final NotificationSettingsService notificationSettingsService;

    public UserPreferenceDto.NotificationSettingsResponse getNotificationSettings(Long userId) {
        UserNotificationSettingEntity settings = notificationSettingsService.getNotificationSettings(userId);
        return mapNotificationSettings(settings);
    }

    public UserPreferenceDto.NotificationSettingsResponse updateNotificationSettings(
            Long userId,
            UserPreferenceDto.UpdateNotificationSettingsRequest request
    ) {
        UserNotificationSettingEntity saved = notificationSettingsService.upsertNotificationSettings(
                userId,
                request.getBefore30m(),
                request.getImportantEventBriefing(),
                request.getLearningReminder()
        );
        return mapNotificationSettings(saved);
    }

    private UserPreferenceDto.NotificationSettingsResponse mapNotificationSettings(
            UserNotificationSettingEntity settings
    ) {
        return UserPreferenceDto.NotificationSettingsResponse.builder()
                .before30m(settings.isBefore30m())
                .importantEventBriefing(settings.isImportantEventBriefing())
                .learningReminder(settings.isLearningReminder())
                .build();
    }
}
