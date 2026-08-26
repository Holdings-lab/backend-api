package com.project.server.repository;

import com.project.server.domain.BrokerSyncHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BrokerSyncHistoryRepository extends JpaRepository<BrokerSyncHistoryEntity, Long> {
    List<BrokerSyncHistoryEntity> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<BrokerSyncHistoryEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<BrokerSyncHistoryEntity> findByStatus(BrokerSyncHistoryEntity.SyncStatus status);

    List<BrokerSyncHistoryEntity> findByAccountIdAndStatus(Long accountId, BrokerSyncHistoryEntity.SyncStatus status);
}
