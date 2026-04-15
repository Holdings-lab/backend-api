package com.project.server.repository;

import com.project.server.domain.UserEventCursorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserEventCursorRepository extends JpaRepository<UserEventCursorEntity, Long> {
    Optional<UserEventCursorEntity> findByUserId(Long userId);
}
