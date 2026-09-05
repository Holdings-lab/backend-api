package com.project.server.repository;

import com.project.server.domain.AccountBalanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountBalanceRepository extends JpaRepository<AccountBalanceEntity, Long> {
    List<AccountBalanceEntity> findByAccountId(Long accountId);

    List<AccountBalanceEntity> findByUserId(Long userId);

    Optional<AccountBalanceEntity> findTopByAccountIdOrderByLastSyncedAtDesc(Long accountId);

    Optional<AccountBalanceEntity> findTopByAccountIdAndAsOfDateOrderByIdDesc(Long accountId, LocalDate asOfDate);

    List<AccountBalanceEntity> findByAccountIdAndAsOfDateGreaterThanOrderByAsOfDateDesc(Long accountId, LocalDate fromDate);

    void deleteByAccountId(Long accountId);
}
