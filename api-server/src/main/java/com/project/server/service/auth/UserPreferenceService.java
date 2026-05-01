package com.project.server.service.auth;

import com.project.server.domain.UserEntity;
import com.project.server.domain.UserNotificationSettingEntity;
import com.project.server.dto.UserPreferenceDto;
import com.project.server.dto.AuthDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final SettingsService settingsService;
    private final UserJpaRepository userJpaRepository;

    public List<AuthDto.SettingMenuItem> getSettings(Long userId) {
        UserNotificationSettingEntity settings = settingsService.getSettings(userId);
        return buildSettingsMenu(settings);
    }

    public List<AuthDto.SettingMenuItem> updateSettings(
            Long userId,
            UserPreferenceDto.UpdateSettingsRequest request) {
        UserEntity user = userJpaRepository.findById(userId).orElse(null);

        if (user == null) {
            throw ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND");
        }

        UserNotificationSettingEntity saved = settingsService.upsertSettings(
                userId,
                request.getBefore30m(),
                request.getImportantEventBriefing(),
                request.getLearningReminder());
        return buildSettingsMenu(saved);
    }

    private List<AuthDto.SettingMenuItem> buildSettingsMenu(
            UserNotificationSettingEntity settings) {
        return List.of(AuthDto.SettingMenuItem.builder()
                .key("notification")
                .title("알림 설정")
                .before30m(settings != null && settings.isBefore30m())
                .importantEventBriefing(settings != null && settings.isImportantEventBriefing())
                .learningReminder(settings != null && settings.isLearningReminder())
                .build());
    }
}
