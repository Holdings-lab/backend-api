package com.project.server.service.integration;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.project.server.domain.PolicyEventEntity;
import com.project.server.domain.UserEntity;
import com.project.server.repository.PolicyEventJpaRepository;
import com.project.server.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final PolicyEventJpaRepository policyEventJpaRepository;
    private final UserJpaRepository userJpaRepository;

    @Async("taskExecutor")
    public void processPushNotification(Long eventId, String keyword) {
        log.info("[비동기 프로세스 시작] eventId: {}, keyword: {}", eventId, keyword);
        
        PolicyEventEntity event = policyEventJpaRepository.findById(eventId).orElse(null);
        if (event == null) {
            log.warn("이벤트 정보를 찾을 수 없습니다: {}", eventId);
            return;
        }

        // 모든 등록된 유저에게 알림 전송
        List<UserEntity> targetUsers = userJpaRepository.findAll();
        log.info("발송 대상 유저 수: {}", targetUsers.size());

        for (UserEntity user : targetUsers) {
            sendFcmMessage(user, event);
        }
    }

    private void sendFcmMessage(UserEntity user, PolicyEventEntity event) {
        // FCM 토큰이 없는 경우 스킵
        if (user.getFcmToken() == null || user.getFcmToken().trim().isEmpty()) {
            log.warn("FCM 토큰이 없어 알림을 건너뜁니다. 유저 ID: {}", user.getId());
            return;
        }

        try {
            Message message = Message.builder()
                    .setToken(user.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle("새로운 정책 이벤트 탐지: " + event.getKeyword())
                            .setBody(event.getTitle())
                            .build())
                    .putData("eventId", String.valueOf(event.getId()))
                    .putData("impactScore", String.valueOf(event.getImpactScore()))
                    .build();

            // Firebase SDK를 사용하여 실제 FCM 메시지 발송
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("[푸시 알림 발송 완료 -> 유저 {}] Token: {} / Message: {} / FCM Response: {}", 
                    user.getId(), user.getFcmToken(), event.getTitle(), response);

        } catch (Exception e) {
            log.error("FCM 발송 실패. 유저 ID: {}", user.getId(), e);
        }
    }
}
