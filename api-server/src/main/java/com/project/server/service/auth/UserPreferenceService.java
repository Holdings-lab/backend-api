package com.project.server.service.auth;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.UserEntity;
import com.project.server.domain.UserNotificationSettingEntity;
import com.project.server.domain.UserProfileEntity;
import com.project.server.domain.asset.UserInvestmentGoalEntity;
import com.project.server.domain.asset.UserInvestmentProfileEntity;
import com.project.server.dto.UserPreferenceDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.UserProfileRepository;
import com.project.server.repository.asset.UserInvestmentGoalRepository;
import com.project.server.repository.asset.UserInvestmentProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final SettingsService settingsService;
    private final UserJpaRepository userJpaRepository;
    private final UserProfileRepository userProfileRepository;
    private final BrokerAccountRepository brokerAccountRepository;
    private final UserInvestmentGoalRepository goalRepository;
    private final UserInvestmentProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public UserPreferenceDto.SettingsHomeResponse getSettingsHome(Long userId) {
        UserEntity user = requireUser(userId);
        UserNotificationSettingEntity settings = settingsService.getSettings(userId);

        String nickname = user.getNickname() != null ? user.getNickname() : user.getEmail();
        String avatarText = userProfileRepository.findByUserId(userId)
                .map(UserProfileEntity::getAvatarText)
                .filter(text -> text != null && !text.isBlank())
                .orElseGet(() -> deriveAvatarInitials(nickname));

        long accountCount = brokerAccountRepository.countByUserId(userId);
        long expiredCount = brokerAccountRepository.countByUserIdAndConnectionStatus(
                userId, BrokerAccountEntity.ConnectionStatus.EXPIRED);

        UserPreferenceDto.SettingsGoal goal = goalRepository.findById(userId)
                .map(this::toGoalSummary)
                .orElse(null);

        int interestsCount = profileRepository.findById(userId)
                .map(UserInvestmentProfileEntity::getInterests)
                .map(set -> set == null ? 0 : set.size())
                .orElse(0);

        return UserPreferenceDto.SettingsHomeResponse.builder()
                .user(UserPreferenceDto.SettingsUser.builder()
                        .nickname(nickname)
                        .email(user.getEmail())
                        .avatarText(avatarText)
                        .build())
                .notifications(toNotificationResponse(settings))
                .investment(UserPreferenceDto.SettingsInvestment.builder()
                        .goal(goal)
                        .connectedAccounts(UserPreferenceDto.ConnectedAccountsSummary.builder()
                                .count(accountCount)
                                .expiredCount(expiredCount)
                                .build())
                        .interests(UserPreferenceDto.InterestsSummary.builder()
                                .count(interestsCount)
                                .build())
                        .build())
                .build();
    }

    @Transactional
    public UserPreferenceDto.NotificationSettingsResponse updateNotificationSettings(
            Long userId,
            UserPreferenceDto.UpdateNotificationSettingsRequest request) {
        requireUser(userId);
        if (request == null) {
            throw ApiException.badRequest("요청 본문이 필요합니다.", "INVALID_REQUEST");
        }

        UserNotificationSettingEntity saved = settingsService.upsertNotificationSettings(
                userId,
                request.getPolicyChangeAlert(),
                request.getBriefingTime());
        return toNotificationResponse(saved);
    }

    @Transactional(readOnly = true)
    public UserPreferenceDto.TestNotificationResponse sendTestNotification(Long userId) {
        UserEntity user = requireUser(userId);
        if (user.getFcmToken() == null || user.getFcmToken().isBlank()) {
            throw ApiException.badRequest("등록된 FCM 토큰이 없습니다.", "FCM_TOKEN_NOT_FOUND");
        }

        try {
            Message message = Message.builder()
                    .setToken(user.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle("테스트 알림")
                            .setBody("PolSignal 테스트 알림입니다.")
                            .build())
                    .putData("type", "TEST")
                    .build();
            FirebaseMessaging.getInstance().send(message);
            log.info("테스트 알림 전송 완료: userId={}", userId);
            return UserPreferenceDto.TestNotificationResponse.builder()
                    .status("SENT")
                    .message("테스트 알림을 보냈어요.")
                    .build();
        } catch (Exception e) {
            log.warn("테스트 알림 전송 실패: userId={}, error={}", userId, e.getMessage());
            throw ApiException.internalServerError(
                    "테스트 알림 전송에 실패했습니다.",
                    "TEST_NOTIFICATION_FAILED");
        }
    }

    private UserPreferenceDto.NotificationSettingsResponse toNotificationResponse(
            UserNotificationSettingEntity settings) {
        boolean policyChangeAlert = settings != null && settings.isPolicyChangeAlert();
        String briefingTime = settings != null && settings.getBriefingTime() != null
                ? settings.getBriefingTime()
                : "09:00";
        return UserPreferenceDto.NotificationSettingsResponse.builder()
                .policyChangeAlert(policyChangeAlert)
                .briefingTime(briefingTime)
                .build();
    }

    private UserPreferenceDto.SettingsGoal toGoalSummary(UserInvestmentGoalEntity goal) {
        if (goal.getFinancialGoal() == null) {
            return null;
        }
        return UserPreferenceDto.SettingsGoal.builder()
                .code(goal.getFinancialGoal().name())
                .label(goal.getGoalLabel() != null && !goal.getGoalLabel().isBlank()
                        ? goal.getGoalLabel()
                        : goal.getFinancialGoal().label())
                .build();
    }

    private UserEntity requireUser(Long userId) {
        return userJpaRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND"));
    }

    private String deriveAvatarInitials(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String trimmed = name.trim();
        return trimmed.substring(0, Math.min(1, trimmed.length()));
    }
}
