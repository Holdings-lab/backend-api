package com.project.server.repository;

import com.project.server.domain.AssetPositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetPositionRepository extends JpaRepository<AssetPositionEntity, Long> {
    List<AssetPositionEntity> findByAccountId(Long accountId);

    List<AssetPositionEntity> findByUserId(Long userId);

    List<AssetPositionEntity> findByUserIdAndAccountId(Long userId, Long accountId);

    void deleteByAccountId(Long accountId);
}
