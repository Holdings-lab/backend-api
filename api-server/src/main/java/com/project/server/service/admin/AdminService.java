package com.project.server.service.admin;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.project.server.domain.NotificationHistoryEntity;
import com.project.server.domain.UserEntity;
import com.project.server.dto.AdminDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.NotificationHistoryRepository;
import com.project.server.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserJpaRepository userJpaRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;

    @Transactional
    public AdminDto.CreateAccountResponse createAccount(AdminDto.CreateAccountRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedNickname = request.getNickname().trim();

        if (userJpaRepository.findByEmail(normalizedEmail).isPresent()) {
            throw ApiException.conflict("이미 존재하는 이메일입니다.", "AUTH_EMAIL_DUPLICATED");
        }

        UserEntity newUser = UserEntity.builder()
                .email(normalizedEmail)
                .nickname(normalizedNickname)
                .password(request.getPassword())
                .fcmToken(request.getFcmToken())
                .build();

        UserEntity saved = userJpaRepository.save(newUser);
        log.info("[Admin] 계정 추가: {}", normalizedEmail);

        return AdminDto.CreateAccountResponse.builder()
                .userId(saved.getId())
                .email(saved.getEmail())
                .nickname(saved.getNickname())
                .message("계정이 성공적으로 추가되었습니다.")
                .build();
    }

    @Transactional
    public AdminDto.DeleteAccountResponse deleteAccount(Long userId) {
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND"));

        String email = user.getEmail();
        userJpaRepository.deleteById(userId);
        log.info("[Admin] 계정 삭제: {}", email);

        return AdminDto.DeleteAccountResponse.builder()
                .userId(userId)
                .email(email)
                .message("계정이 성공적으로 삭제되었습니다.")
                .build();
    }

    @Transactional
    public AdminDto.SendNotificationResponse sendNotification(AdminDto.SendNotificationRequest request) {
        List<UserEntity> targetUsers;

        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            targetUsers = userJpaRepository.findAll();
            log.info("[Admin] 모든 사용자에게 알림 전송 시작: {}", targetUsers.size());
        } else {
            targetUsers = userJpaRepository.findAllById(request.getUserIds());
            log.info("[Admin] 특정 사용자에게 알림 전송 시작: {}", targetUsers.size());
        }

        int successCount = 0;
        int failureCount = 0;

        for (UserEntity user : targetUsers) {
            try {
                sendFcmMessage(user, request);
                recordNotificationHistory(user.getId(), request, "SENT", null, null);
                successCount++;
            } catch (Exception e) {
                String errorCode = resolveErrorCode(e);
                recordNotificationHistory(user.getId(), request, "FAILED", errorCode, e.getMessage());
                failureCount++;
                log.warn("[Admin] 알림 전송 실패: userId={}, error={}", user.getId(), e.getMessage());
            }
        }

        log.info("[Admin] 알림 전송 완료: 성공={}, 실패={}", successCount, failureCount);
        return AdminDto.SendNotificationResponse.builder()
                .successCount(successCount)
                .failureCount(failureCount)
                .message(String.format("알림 전송 완료 - 성공: %d, 실패: %d", successCount, failureCount))
                .build();
    }

    private void sendFcmMessage(UserEntity user, AdminDto.SendNotificationRequest request) {
        if (user.getFcmToken() == null || user.getFcmToken().trim().isEmpty()) {
            throw new RuntimeException("FCM 토큰이 없습니다.");
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(user.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle(request.getTitle())
                            .setBody(request.getMessage())
                            .build());

            if (request.getDeeplink() != null && !request.getDeeplink().isEmpty()) {
                messageBuilder.putData("deeplink", request.getDeeplink());
            }

            FirebaseMessaging.getInstance().send(messageBuilder.build());
        } catch (Exception e) {
            throw new RuntimeException("FCM 전송 실패: " + e.getMessage(), e);
        }
    }

    @Transactional
    private void recordNotificationHistory(
            Long userId,
            AdminDto.SendNotificationRequest request,
            String status,
            String errorCode,
            String errorMessage
    ) {
        NotificationHistoryEntity history = NotificationHistoryEntity.builder()
                .userId(userId)
                .title(request.getTitle())
                // 실무 운영 기준으로 payload 원문 대신 핵심 필드만 저장
                .message(null)
                .deeplink(null)
                .status(status)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .sentAt(LocalDateTime.now())
                .build();

        notificationHistoryRepository.save(history);
    }

    private String resolveErrorCode(Exception e) {
        if (e instanceof ApiException apiException) {
            return apiException.getErrorCode();
        }
        return "ADMIN_NOTIFICATION_SEND_FAILED";
    }

    public AdminDto.UserListResponse getUserList(int page, int size) {
        List<UserEntity> users = userJpaRepository.findAll();

        List<AdminDto.UserDetailResponse> userDetails = users.stream()
                .map(user -> AdminDto.UserDetailResponse.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .fcmToken(user.getFcmToken())
                        .createdAt(0L)
                        .updatedAt(0L)
                        .build())
                .toList();

        return AdminDto.UserListResponse.builder()
                .totalCount(userDetails.size())
                .users(userDetails)
                .build();
    }

    @Transactional
    public AdminDto.CreateAccountResponse updateUserFcmToken(Long userId, String fcmToken) {
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND"));

        user.setFcmToken(fcmToken);
        userJpaRepository.save(user);
        log.info("[Admin] FCM 토큰 업데이트: userId={}", userId);

        return AdminDto.CreateAccountResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .message("FCM 토큰이 업데이트되었습니다.")
                .build();
    }

    @Transactional
    public AdminDto.CreateAccountResponse changePassword(Long userId, String newPassword) {
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND"));

        user.setPassword(newPassword);
        userJpaRepository.save(user);
        log.info("[Admin] 비밀번호 변경: userId={}", userId);

        return AdminDto.CreateAccountResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .message("비밀번호가 변경되었습니다.")
                .build();
    }
}
