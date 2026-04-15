package com.project.server.repository;

import com.project.server.domain.NotificationHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationHistoryRepository extends JpaRepository<NotificationHistoryEntity, Long> {
    List<NotificationHistoryEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<NotificationHistoryEntity> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    long deleteBySentAtBefore(LocalDateTime cutoff);
}
