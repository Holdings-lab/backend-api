package com.project.server.repository.asset;

import com.project.server.domain.asset.UserSyncStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSyncStateRepository extends JpaRepository<UserSyncStateEntity, Long> {
}
