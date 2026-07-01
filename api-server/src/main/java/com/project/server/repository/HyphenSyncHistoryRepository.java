package com.project.server.repository;

import com.project.server.domain.HyphenSyncHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HyphenSyncHistoryRepository extends JpaRepository<HyphenSyncHistoryEntity, Long> {
    List<HyphenSyncHistoryEntity> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<HyphenSyncHistoryEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<HyphenSyncHistoryEntity> findByStatus(HyphenSyncHistoryEntity.SyncStatus status);

    List<HyphenSyncHistoryEntity> findByAccountIdAndStatus(Long accountId, HyphenSyncHistoryEntity.SyncStatus status);
}
