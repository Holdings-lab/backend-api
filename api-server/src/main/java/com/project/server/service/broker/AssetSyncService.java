package com.project.server.service.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.CodefSyncHistoryEntity;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.AccountBalanceRepository;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.repository.CodefSyncHistoryRepository;
import com.project.server.service.integration.CodefApiClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AssetSyncService {

    private final BrokerAccountRepository brokerAccountRepository;
    private final AssetPositionRepository assetPositionRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final CodefSyncHistoryRepository syncHistoryRepository;
    private final CodefApiClientService codefApiClientService;

    /**
     * 즉시 동기화 요청
     */
    public BrokerAccountDto.SyncResponse requestSync(Long userId, Long accountId, String syncType) {
        BrokerAccountEntity account = validateAccountAccess(userId, accountId);

        if (account.getCodefStatus() != BrokerAccountEntity.CodefStatus.CONNECTED) {
            throw ApiException.badRequest("연동되지 않은 계좌입니다.", "ACCOUNT_NOT_CONNECTED");
        }

        String syncId = UUID.randomUUID().toString();

        // 동기화 시작 기록
        CodefSyncHistoryEntity history = CodefSyncHistoryEntity.builder()
                .accountId(accountId)
                .userId(userId)
                .syncType(syncType)
                .status(CodefSyncHistoryEntity.SyncStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();
        syncHistoryRepository.save(history);

        // 비동기로 동기화 수행 (실제 구현에서는 별도 스레드/큐 사용)
        try {
            performSync(account, syncType);

            history.setStatus(CodefSyncHistoryEntity.SyncStatus.SUCCESS);
            history.setCompletedAt(LocalDateTime.now());
            history.setSyncDurationMs((int) (System.currentTimeMillis() - history.getStartedAt().toString().hashCode()));

        } catch (Exception e) {
            log.error("Sync failed for account: {}", accountId, e);
            history.setStatus(CodefSyncHistoryEntity.SyncStatus.FAILURE);
            history.setErrorMessage(e.getMessage());
            history.setCompletedAt(LocalDateTime.now());
        }

        syncHistoryRepository.save(history);
        account.setSyncCount((account.getSyncCount() != null ? account.getSyncCount() : 0) + 1);
        account.setLastSyncedAt(LocalDateTime.now());
        brokerAccountRepository.save(account);

        return BrokerAccountDto.SyncResponse.builder()
                .syncId(syncId)
                .status("STARTED")
                .syncType(syncType)
                .startedAt(LocalDateTime.now())
                .build();
    }

    /**
     * 정기 동기화 (스케줄러)
     */
    @Scheduled(cron = "${codef.sync.schedule-cron:0 0 12,18 * * *}")
    public void scheduledSync() {
        log.info("Starting scheduled CODEF sync...");

        List<BrokerAccountEntity> connectedAccounts = brokerAccountRepository
                .findByCodefStatusIn(List.of(BrokerAccountEntity.CodefStatus.CONNECTED));

        connectedAccounts.forEach(account -> {
            try {
                performSync(account, "BALANCE,POSITION");
                account.setLastSyncedAt(LocalDateTime.now());
                brokerAccountRepository.save(account);
            } catch (Exception e) {
                log.error("Scheduled sync failed for account: {}", account.getId(), e);
                account.setCodefStatus(BrokerAccountEntity.CodefStatus.ERROR);
                brokerAccountRepository.save(account);
            }
        });

        log.info("Scheduled sync completed");
    }

    /**
     * 실제 동기화 수행
     */
    private void performSync(BrokerAccountEntity account, String syncType) {
        String adminToken = codefApiClientService.getAdminAccessToken();
        String connectedId = account.getConnectedId();

        if (connectedId == null || connectedId.isBlank()) {
            throw new RuntimeException("Account missing connectedId for CODEF scraping");
        }

        if (syncType.contains("BALANCE") || syncType.equals("ALL")) {
            syncBalance(account, adminToken, connectedId);
        }

        if (syncType.contains("POSITION") || syncType.equals("ALL")) {
            syncPositions(account, adminToken, connectedId);
        }

        if (syncType.contains("HISTORY") || syncType.equals("ALL")) {
            syncHistory(account, adminToken, connectedId);
        }
    }

    /**
     * 계좌 잔액 동기화
     */
    private void syncBalance(BrokerAccountEntity account, String adminToken, String connectedId) {
        try {
            JsonNode balanceData = codefApiClientService.fetchAccountBalance(adminToken, connectedId, account.getAccountNumber());

            if (balanceData == null || !balanceData.has("result")) {
                throw new RuntimeException("Invalid balance response");
            }

            JsonNode data = balanceData.path("result").path("data");

            AccountBalanceEntity balance = AccountBalanceEntity.builder()
                    .accountId(account.getId())
                    .userId(account.getUserId())
                    .totalAssetValue(new BigDecimal(data.path("totalAsset").asText("0")))
                    .cashBalance(new BigDecimal(data.path("cashBalance").asText("0")))
                    .depositAmount(new BigDecimal(data.path("depositAmount").asText("0")))
                    .evaluationAmount(new BigDecimal(data.path("evaluationAmount").asText("0")))
                    .gainLoss(new BigDecimal(data.path("gainLoss").asText("0")))
                    .gainLossRate(new BigDecimal(data.path("gainLossRate").asText("0")))
                    .dailyGainLoss(new BigDecimal(data.path("dailyGainLoss").asText("0")))
                    .dailyGainLossRate(new BigDecimal(data.path("dailyGainLossRate").asText("0")))
                    .asOfDate(LocalDate.now())
                    .lastSyncedAt(LocalDateTime.now())
                    .build();

            accountBalanceRepository.save(balance);
            log.info("Balance synced for account: {}", account.getId());

        } catch (Exception e) {
            log.error("Error syncing balance for account: {}", account.getId(), e);
            throw new RuntimeException("Balance sync failed", e);
        }
    }

    /**
     * 보유 자산(포지션) 동기화
     */
    private void syncPositions(BrokerAccountEntity account, String adminToken, String connectedId) {
        try {
            JsonNode holdingData = codefApiClientService.fetchHoldingAssets(adminToken, connectedId, account.getAccountNumber());

            if (holdingData == null || !holdingData.has("result")) {
                throw new RuntimeException("Invalid holding response");
            }

            JsonNode positions = holdingData.path("result").path("data").path("holdings");

            // 기존 포지션 삭제
            assetPositionRepository.deleteByAccountId(account.getId());

            // 새로운 포지션 저장
            if (positions.isArray()) {
                for (JsonNode position : positions) {
                    AssetPositionEntity entity = AssetPositionEntity.builder()
                            .accountId(account.getId())
                            .userId(account.getUserId())
                            .symbol(position.path("symbol").asText())
                            .positionType(position.path("type").asText("STOCK"))
                            .quantity(new BigDecimal(position.path("quantity").asText("0")))
                            .purchasePrice(new BigDecimal(position.path("purchasePrice").asText("0")))
                            .currentPrice(new BigDecimal(position.path("currentPrice").asText("0")))
                            .currentValue(new BigDecimal(position.path("currentValue").asText("0")))
                            .purchaseAmount(new BigDecimal(position.path("purchaseAmount").asText("0")))
                            .gainLoss(new BigDecimal(position.path("gainLoss").asText("0")))
                            .gainLossRate(new BigDecimal(position.path("gainLossRate").asText("0")))
                            .currencyCode(position.path("currency").asText("KRW"))
                            .lastSyncedAt(LocalDateTime.now())
                            .build();

                    assetPositionRepository.save(entity);
                }
            }

            log.info("Positions synced for account: {}", account.getId());

        } catch (Exception e) {
            log.error("Error syncing positions for account: {}", account.getId(), e);
            throw new RuntimeException("Position sync failed", e);
        }
    }

    /**
     * 거래 내역 동기화 (필요시)
     */
        private void syncHistory(BrokerAccountEntity account, String adminToken, String connectedId) {
        try {
            // 지난 90일 거래 내역 조회
            LocalDate fromDate = LocalDate.now().minusDays(90);
            LocalDate toDate = LocalDate.now();

            JsonNode historyData = codefApiClientService.fetchTransactionHistory(
                adminToken,
                connectedId,
                account.getAccountNumber(),
                fromDate.toString(),
                toDate.toString()
            );

            if (historyData == null || !historyData.has("result")) {
                log.warn("Empty transaction history for account: {}", account.getId());
                return;
            }

            // 거래 내역은 별도 테이블에 저장 (구현 생략)
            log.info("History synced for account: {}", account.getId());

        } catch (Exception e) {
            log.error("Error syncing history for account: {}", account.getId(), e);
            // 거래 내역 조회 실패는 치명적이지 않으므로 로그만 기록
        }
    }

    /**
     * 동기화 상태 조회
     */
    @Transactional(readOnly = true)
    public BrokerAccountDto.SyncStatusResponse getSyncStatus(Long userId, Long accountId, String syncId) {
        validateAccountAccess(userId, accountId);

        List<CodefSyncHistoryEntity> histories = syncHistoryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);

        CodefSyncHistoryEntity history = histories.stream()
                .filter(h -> h.getId().toString().contains(syncId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("동기화 기록을 찾을 수 없습니다.", "SYNC_NOT_FOUND"));

        return BrokerAccountDto.SyncStatusResponse.builder()
                .syncId(syncId)
                .status(history.getStatus().name())
                .syncType(history.getSyncType())
                .recordCount(history.getRecordCount())
                .syncDurationMs(history.getSyncDurationMs())
                .errorMessage(history.getErrorMessage())
                .startedAt(history.getStartedAt())
                .completedAt(history.getCompletedAt())
                .build();
    }

    /**
     * 동기화 이력 조회
     */
    @Transactional(readOnly = true)
    public List<BrokerAccountDto.SyncHistoryResponse> getSyncHistory(Long userId, Long accountId) {
        validateAccountAccess(userId, accountId);

        return syncHistoryRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(history -> BrokerAccountDto.SyncHistoryResponse.builder()
                        .id(history.getId())
                        .syncType(history.getSyncType())
                        .status(history.getStatus().name())
                        .recordCount(history.getRecordCount())
                        .syncDurationMs(history.getSyncDurationMs())
                        .errorMessage(history.getErrorMessage())
                        .startedAt(history.getStartedAt())
                        .completedAt(history.getCompletedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 계좌 접근 권한 검증
     */
    private BrokerAccountEntity validateAccountAccess(Long userId, Long accountId) {
        BrokerAccountEntity account = brokerAccountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("계좌를 찾을 수 없습니다.", "ACCOUNT_NOT_FOUND"));

        if (!account.getUserId().equals(userId)) {
            throw ApiException.badRequest("접근 권한이 없습니다.", "FORBIDDEN_ACCESS");
        }

        return account;
    }
}
