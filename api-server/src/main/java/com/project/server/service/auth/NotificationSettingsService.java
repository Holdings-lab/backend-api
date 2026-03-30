package com.project.server.service.auth;

import com.project.server.domain.UserNotificationSettingEntity;
import com.project.server.repository.UserNotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final UserNotificationSettingRepository notificationSettingRepository;

    @Transactional(readOnly = true)
    public UserNotificationSettingEntity getNotificationSettings(Long userId) {
        return notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> UserNotificationSettingEntity.builder()
                        .userId(userId)
                        .before30m(true)
                        .importantEventBriefing(false)
                        .learningReminder(true)
                        .build());
    }

    @Transactional
    public UserNotificationSettingEntity upsertNotificationSettings(Long userId,
                                                                    Boolean before30m,
                                                                    Boolean importantEventBriefing,
                                                                    Boolean learningReminder) {
        UserNotificationSettingEntity current = notificationSettingRepository.findByUserId(userId)
                .orElseGet(() -> UserNotificationSettingEntity.builder()
                        .userId(userId)
                        .before30m(true)
                        .importantEventBriefing(false)
                        .learningReminder(true)
                        .build());

        if (before30m != null) {
            current.setBefore30m(before30m);
        }
        if (importantEventBriefing != null) {
            current.setImportantEventBriefing(importantEventBriefing);
        }
        if (learningReminder != null) {
            current.setLearningReminder(learningReminder);
        }

        return notificationSettingRepository.save(current);
    }
}
