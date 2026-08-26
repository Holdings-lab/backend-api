package com.project.server.repository;

import com.project.server.domain.BrokerAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrokerAccountRepository extends JpaRepository<BrokerAccountEntity, Long> {
    List<BrokerAccountEntity> findByUserId(Long userId);

    Optional<BrokerAccountEntity> findByUserIdAndBrokerNameAndAccountNumber(Long userId, String brokerName, String accountNumber);

    Optional<BrokerAccountEntity> findByUserIdAndIsPrimary(Long userId, Boolean isPrimary);

    List<BrokerAccountEntity> findByConnectionStatusIn(List<BrokerAccountEntity.ConnectionStatus> statuses);

    long countByUserId(Long userId);

    long countByUserIdAndConnectionStatus(Long userId, BrokerAccountEntity.ConnectionStatus connectionStatus);
}
