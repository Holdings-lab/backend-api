package com.project.server.repository;

import com.project.server.domain.UserWatchAssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserWatchAssetRepository extends JpaRepository<UserWatchAssetEntity, Long> {
    List<UserWatchAssetEntity> findByUserIdOrderByDisplayOrderAsc(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
