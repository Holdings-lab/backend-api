package com.project.server.repository;

import com.project.server.domain.PolicyEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PolicyEventJpaRepository extends JpaRepository<PolicyEventEntity, Long> {
	Optional<PolicyEventEntity> findTopByOrderByCreatedAtDesc();
}
