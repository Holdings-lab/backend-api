package com.project.server.repository.asset;

import com.project.server.domain.asset.UserAssetAthEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAssetAthRepository extends JpaRepository<UserAssetAthEntity, Long> {
}
