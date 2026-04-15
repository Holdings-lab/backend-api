package com.project.server.repository;

import com.project.server.domain.FeaturedEventStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FeaturedEventStateRepository extends JpaRepository<FeaturedEventStateEntity, Long> {
    Optional<FeaturedEventStateEntity> findByUserId(Long userId);
}
