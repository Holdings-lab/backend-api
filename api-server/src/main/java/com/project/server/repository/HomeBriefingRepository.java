package com.project.server.repository;

import com.project.server.domain.HomeBriefingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface HomeBriefingRepository extends JpaRepository<HomeBriefingEntity, Long> {
    Optional<HomeBriefingEntity> findTopByUserIdAndBriefingDateOrderByUpdatedAtDesc(Long userId, LocalDate briefingDate);

    Optional<HomeBriefingEntity> findTopByUserIdOrderByBriefingDateDescUpdatedAtDesc(Long userId);
}
