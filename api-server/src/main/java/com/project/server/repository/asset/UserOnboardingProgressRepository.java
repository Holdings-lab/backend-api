package com.project.server.repository.asset;

import com.project.server.domain.asset.UserOnboardingProgressEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOnboardingProgressRepository extends JpaRepository<UserOnboardingProgressEntity, Long> {
}
