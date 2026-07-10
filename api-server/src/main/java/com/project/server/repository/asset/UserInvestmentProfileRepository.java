package com.project.server.repository.asset;

import com.project.server.domain.asset.UserInvestmentProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInvestmentProfileRepository extends JpaRepository<UserInvestmentProfileEntity, Long> {
}
