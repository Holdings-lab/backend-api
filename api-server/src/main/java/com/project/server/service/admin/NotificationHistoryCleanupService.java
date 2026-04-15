package com.project.server.service.admin;

import com.project.server.repository.NotificationHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationHistoryCleanupService {

    private final NotificationHistoryRepository notificationHistoryRepository;

    @Value("${admin.notification-history.retention-days:30}")
    private int retentionDays;

    // 매일 새벽 3시에 30일 초과 이력을 정리한다.
    @Scheduled(cron = "${admin.notification-history.cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void cleanupOldNotificationHistory() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long deletedCount = notificationHistoryRepository.deleteBySentAtBefore(cutoff);

        if (deletedCount > 0) {
            log.info("[Admin] notification_history 정리 완료: {}건 삭제 (기준일: {})", deletedCount, cutoff);
        }
    }
}
