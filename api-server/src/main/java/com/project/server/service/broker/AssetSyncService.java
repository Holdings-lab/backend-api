package com.project.server.service.broker;

import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.BrokerSyncHistoryEntity;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.AccountBalanceRepository;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.repository.BrokerSyncHistoryRepository;
import com.project.server.service.integration.kis.KisApiClient;
import com.project.server.service.integration.kis.KisCredentialResolver;
import com.project.server.service.integration.kis.KisFieldMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AssetSyncService {

    private final BrokerAccountRepository brokerAccountRepository;
    private final AssetPositionRepository assetPositionRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final BrokerSyncHistoryRepository syncHistoryRepository;
    private final KisApiClient kisApiClient;
    private final KisCredentialResolver kisCredentialResolver;

    public BrokerAccountDto.SyncResponse requestSync(Long userId, Long accountId) {
        BrokerAccountEntity account = validateAccountAccess(userId, accountId);

        if (account.getConnectionStatus() != BrokerAccountEntity.ConnectionStatus.CONNECTED) {
            throw ApiException.badRequest("연동되지 않은 계좌입니다.", "ACCOUNT_NOT_CONNECTED");
        }

        long startedAtMs = System.currentTimeMillis();
        BrokerSyncHistoryEntity history = BrokerSyncHistoryEntity.builder()
                .accountId(accountId)
                .userId(userId)
                .syncType("ALL")
                .status(BrokerSyncHistoryEntity.SyncStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();
        syncHistoryRepository.save(history);

        Long syncId = history.getId();

        try {
            int recordCount = performSync(account);

            history.setStatus(BrokerSyncHistoryEntity.SyncStatus.SUCCESS);
            history.setRecordCount(recordCount);
            history.setCompletedAt(LocalDateTime.now());
            history.setSyncDurationMs((int) (System.currentTimeMillis() - startedAtMs));

        } catch (Exception e) {
            log.error("Sync failed for account: {}", accountId, e);
            history.setStatus(BrokerSyncHistoryEntity.SyncStatus.FAILURE);
            history.setErrorMessage(e.getMessage());
            history.setCompletedAt(LocalDateTime.now());
            history.setSyncDurationMs((int) (System.currentTimeMillis() - startedAtMs));
        }

        syncHistoryRepository.save(history);
        account.setSyncCount((account.getSyncCount() != null ? account.getSyncCount() : 0) + 1);
        account.setLastSyncedAt(LocalDateTime.now());
        brokerAccountRepository.save(account);

        return BrokerAccountDto.SyncResponse.builder()
                .syncId(syncId)
                .status(history.getStatus().name())
                .syncType(history.getSyncType())
                .startedAt(history.getStartedAt())
                .build();
    }

    public void syncAllConnectedAccounts(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }

        List<BrokerAccountEntity> connectedAccounts = brokerAccountRepository.findByUserId(userId).stream()
                .filter(account -> account.getConnectionStatus() == BrokerAccountEntity.ConnectionStatus.CONNECTED)
                .toList();

        if (connectedAccounts.isEmpty()) {
            return;
        }

        List<String> failures = new ArrayList<>();
        for (BrokerAccountEntity account : connectedAccounts) {
            try {
                performSync(account);
                account.setSyncCount((account.getSyncCount() != null ? account.getSyncCount() : 0) + 1);
                account.setLastSyncedAt(LocalDateTime.now());
                brokerAccountRepository.save(account);
            } catch (Exception e) {
                log.error("Sync failed for account: {}", account.getId(), e);
                failures.add(account.getId() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            throw ApiException.internalServerError(
                    "연동 계좌 동기화에 실패했습니다.",
                    "MULTI_ACCOUNT_SYNC_FAILED");
        }
    }

    @Scheduled(cron = "${kis.sync.schedule-cron:0 0 12,18 * * *}")
    @ConditionalOnProperty(name = "kis.sync.global-schedule-enabled", havingValue = "true")
    public void scheduledSync() {
        log.info("Starting scheduled broker sync...");

        List<BrokerAccountEntity> connectedAccounts = brokerAccountRepository
                .findByConnectionStatusIn(List.of(BrokerAccountEntity.ConnectionStatus.CONNECTED));

        connectedAccounts.forEach(account -> {
            try {
                performSync(account);
                account.setLastSyncedAt(LocalDateTime.now());
                brokerAccountRepository.save(account);
            } catch (Exception e) {
                log.error("Scheduled sync failed for account: {}", account.getId(), e);
                account.setConnectionStatus(BrokerAccountEntity.ConnectionStatus.ERROR);
                brokerAccountRepository.save(account);
            }
        });

        log.info("Scheduled broker sync completed");
    }

    public int persistSnapshot(BrokerAccountEntity account, KisApiClient.KisBalanceSnapshot snapshot) {
        saveBalance(account, snapshot);
        return savePositions(account, snapshot);
    }

    private int performSync(BrokerAccountEntity account) {
        KisApiClient.KisCredential credential = kisCredentialResolver.resolve(account);
        KisApiClient.KisBalanceSnapshot snapshot = kisApiClient.fetchBalance(credential);
        return persistSnapshot(account, snapshot);
    }

    private void saveBalance(BrokerAccountEntity account, KisApiClient.KisBalanceSnapshot snapshot) {
        BigDecimal cashBalance = defaultDecimal(snapshot.cashBalance());
        BigDecimal evaluationAmount = defaultDecimal(snapshot.evaluationAmount());
        BigDecimal purchaseAmount = defaultDecimal(snapshot.purchaseAmount());
        BigDecimal gainLoss = defaultDecimal(snapshot.gainLoss());
        BigDecimal gainLossRate = defaultDecimal(snapshot.gainLossRate());
        BigDecimal totalAssetValue = defaultDecimal(snapshot.totalAssetValue());
        if (totalAssetValue.compareTo(BigDecimal.ZERO) == 0) {
            totalAssetValue = evaluationAmount.add(cashBalance);
        }

        AccountBalanceEntity balance = AccountBalanceEntity.builder()
                .accountId(account.getId())
                .userId(account.getUserId())
                .totalAssetValue(totalAssetValue)
                .cashBalance(cashBalance)
                .depositAmount(purchaseAmount)
                .evaluationAmount(evaluationAmount)
                .gainLoss(gainLoss)
                .gainLossRate(gainLossRate)
                .dailyGainLoss(BigDecimal.ZERO)
                .dailyGainLossRate(BigDecimal.ZERO)
                .currencyCode("KRW")
                .fxRatesJson(KisFieldMapper.toFxRatesJson(snapshot.fxRates()))
                .asOfDate(LocalDate.now())
                .lastSyncedAt(LocalDateTime.now())
                .build();

        accountBalanceRepository.save(balance);
        log.info("KIS balance synced for account: {}", account.getId());
    }

    private int savePositions(BrokerAccountEntity account, KisApiClient.KisBalanceSnapshot snapshot) {
        assetPositionRepository.deleteByAccountId(account.getId());
        int savedCount = 0;
        if (snapshot.positions() == null) {
            return savedCount;
        }
        for (KisApiClient.KisPosition position : snapshot.positions()) {
            if (position.itemCode() == null || position.itemCode().isBlank()) {
                continue;
            }
            if (!"Y".equalsIgnoreCase(position.overseasYn())) {
                continue;
            }
            KisApiClient.NativeQuote nativeQuote = position.nativeQuote();
            KisApiClient.KrwQuote krw = position.krw();
            AssetPositionEntity entity = AssetPositionEntity.builder()
                    .accountId(account.getId())
                    .userId(account.getUserId())
                    .symbol(position.itemCode())
                    .itemCode(position.itemCode())
                    .itemName(position.itemName())
                    .positionType(defaultString(position.productType(), "STOCK"))
                    .productCode(position.productCode())
                    .overseasYn(position.overseasYn())
                    .quantity(defaultDecimal(position.quantity()))
                    .purchasePrice(nativeQuote == null ? BigDecimal.ZERO : defaultDecimal(nativeQuote.purchaseUnitPrice()))
                    .currentPrice(nativeQuote == null ? BigDecimal.ZERO : defaultDecimal(nativeQuote.presentPrice()))
                    .nativePurchaseAmount(nativeQuote == null ? BigDecimal.ZERO : defaultDecimal(nativeQuote.purchaseAmount()))
                    .nativeValuationAmount(nativeQuote == null ? BigDecimal.ZERO : defaultDecimal(nativeQuote.valuationAmount()))
                    .nativeGainLoss(nativeQuote == null ? BigDecimal.ZERO : defaultDecimal(nativeQuote.gainLoss()))
                    .purchaseAmount(krw == null ? BigDecimal.ZERO : defaultDecimal(krw.purchaseAmount()))
                    .currentValue(krw == null ? BigDecimal.ZERO : defaultDecimal(krw.valuationAmount()))
                    .gainLoss(krw == null ? BigDecimal.ZERO : defaultDecimal(krw.gainLoss()))
                    .gainLossRate(defaultDecimal(position.profitRate()))
                    .currencyCode(defaultString(position.currencyCode(), "USD"))
                    .fxRate(defaultDecimal(position.fxRate()))
                    .lastSyncedAt(LocalDateTime.now())
                    .build();
            assetPositionRepository.save(entity);
            savedCount++;
        }
        log.info("KIS positions synced for account: {}, count={}", account.getId(), savedCount);
        return savedCount;
    }

    @Transactional(readOnly = true)
    public BrokerAccountDto.SyncStatusResponse getSyncStatus(Long userId, Long accountId, Long syncId) {
        validateAccountAccess(userId, accountId);

        List<BrokerSyncHistoryEntity> histories = syncHistoryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);

        BrokerSyncHistoryEntity history = histories.stream()
                .filter(h -> h.getId().equals(syncId))
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

    private BrokerAccountEntity validateAccountAccess(Long userId, Long accountId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
        if (accountId == null || accountId <= 0) {
            throw ApiException.badRequest("유효하지 않은 계좌 ID입니다.", "INVALID_ACCOUNT_ID");
        }

        BrokerAccountEntity account = brokerAccountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("계좌를 찾을 수 없습니다.", "ACCOUNT_NOT_FOUND"));

        if (!account.getUserId().equals(userId)) {
            throw ApiException.badRequest("해당 계좌의 소유자와 요청한 사용자가 일치하지 않습니다.", "USER_MISMATCH");
        }

        return account;
    }

    private static String defaultString(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
