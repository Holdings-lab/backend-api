package com.project.server.repository;

import com.project.server.domain.UserEventAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserEventAlertRepository extends JpaRepository<UserEventAlertEntity, Long> {
    Optional<UserEventAlertEntity> findByUserIdAndEventId(Long userId, Long eventId);
}
