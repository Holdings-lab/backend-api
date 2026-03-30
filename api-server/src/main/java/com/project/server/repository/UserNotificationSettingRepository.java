package com.project.server.repository;

import com.project.server.domain.UserNotificationSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserNotificationSettingRepository extends JpaRepository<UserNotificationSettingEntity, Long> {
    Optional<UserNotificationSettingEntity> findByUserId(Long userId);
}
