package com.project.server.repository.asset;

import com.project.server.domain.asset.AssetSnapshotType;
import com.project.server.domain.asset.UserAssetSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface UserAssetSnapshotRepository extends JpaRepository<UserAssetSnapshotEntity, Long> {
    Optional<UserAssetSnapshotEntity> findTopByUserIdAndSnapshotTypeOrderBySnapshotDateDesc(
            Long userId, AssetSnapshotType snapshotType);

    Optional<UserAssetSnapshotEntity> findByUserIdAndSnapshotTypeAndSnapshotDate(
            Long userId, AssetSnapshotType snapshotType, LocalDate snapshotDate);
}
