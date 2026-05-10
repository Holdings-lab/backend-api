package com.project.server.repository;

import com.project.server.domain.CodefSyncHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CodefSyncHistoryRepository extends JpaRepository<CodefSyncHistoryEntity, Long> {
    List<CodefSyncHistoryEntity> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<CodefSyncHistoryEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<CodefSyncHistoryEntity> findByStatus(CodefSyncHistoryEntity.SyncStatus status);

    List<CodefSyncHistoryEntity> findByAccountIdAndStatus(Long accountId, CodefSyncHistoryEntity.SyncStatus status);
}
