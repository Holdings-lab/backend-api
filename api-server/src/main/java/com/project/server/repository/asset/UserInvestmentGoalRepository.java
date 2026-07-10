package com.project.server.repository.asset;

import com.project.server.domain.asset.UserInvestmentGoalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserInvestmentGoalRepository extends JpaRepository<UserInvestmentGoalEntity, Long> {
}
