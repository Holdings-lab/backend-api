package com.project.server.service.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.HyphenSyncHistoryEntity;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.AccountBalanceRepository;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.repository.HyphenSyncHistoryRepository;
import com.project.server.service.integration.HyphenApiClient;
import com.project.server.service.security.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AssetSyncService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BrokerAccountRepository brokerAccountRepository;
    private final AssetPositionRepository assetPositionRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final HyphenSyncHistoryRepository syncHistoryRepository;
    private final HyphenApiClient apiClient;
    private final CryptoService cryptoService;

    public BrokerAccountDto.SyncResponse requestSync(Long userId, Long accountId) {
        BrokerAccountEntity account = validateAccountAccess(userId, accountId);

        if (account.getHyphenStatus() != BrokerAccountEntity.HyphenStatus.CONNECTED) {
            throw ApiException.badRequest("연동되지 않은 계좌입니다.", "ACCOUNT_NOT_CONNECTED");
        }

        long startedAtMs = System.currentTimeMillis();
        HyphenSyncHistoryEntity history = HyphenSyncHistoryEntity.builder()
                .accountId(accountId)
                .userId(userId)
                .syncType("ALL")
                .status(HyphenSyncHistoryEntity.SyncStatus.PENDING)
                .startedAt(LocalDateTime.now())
                .build();
        syncHistoryRepository.save(history);

        Long syncId = history.getId();

        try {
            performSync(account);

            history.setStatus(HyphenSyncHistoryEntity.SyncStatus.SUCCESS);
            history.setCompletedAt(LocalDateTime.now());
            history.setSyncDurationMs((int) (System.currentTimeMillis() - startedAtMs));

        } catch (Exception e) {
            log.error("Sync failed for account: {}", accountId, e);
            history.setStatus(HyphenSyncHistoryEntity.SyncStatus.FAILURE);
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
                .filter(account -> account.getHyphenStatus() == BrokerAccountEntity.HyphenStatus.CONNECTED)
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

    @Scheduled(cron = "${hyphen.sync.schedule-cron:0 0 12,18 * * *}")
    @ConditionalOnProperty(name = "hyphen.sync.global-schedule-enabled", havingValue = "true")
    public void scheduledSync() {
        log.info("Starting scheduled broker sync...");

        List<BrokerAccountEntity> connectedAccounts = brokerAccountRepository
                .findByHyphenStatusIn(List.of(BrokerAccountEntity.HyphenStatus.CONNECTED));

        connectedAccounts.forEach(account -> {
            try {
                performSync(account);
                account.setLastSyncedAt(LocalDateTime.now());
                brokerAccountRepository.save(account);
            } catch (Exception e) {
                log.error("Scheduled sync failed for account: {}", account.getId(), e);
                account.setHyphenStatus(BrokerAccountEntity.HyphenStatus.ERROR);
                brokerAccountRepository.save(account);
            }
        });

        log.info("Scheduled broker sync completed");
    }

    private void performSync(BrokerAccountEntity account) {
        HyphenApiClient.HyphenCredential credential = buildCredential(account);

        syncHoldings(account, credential);
        syncCashBalance(account, credential);
        syncDepositWithdrawHistory(account, credential);
        syncAssetTransactionHistory(account, credential);
    }

    private HyphenApiClient.HyphenCredential buildCredential(BrokerAccountEntity account) {
        String hyphenUserId = decryptRequired(account.getHyphenUserId(), "NO_HYPHEN_USER_ID");
        String hyphenUserPw = decryptRequired(account.getHyphenUserPassword(), "NO_HYPHEN_USER_PW");
        String hyphenAccountPassword = decryptOptional(account.getHyphenAccountPassword());

        return new HyphenApiClient.HyphenCredential(
                hyphenUserId,
                hyphenUserPw,
                "ID",
                "N",
                hyphenAccountPassword);
    }

    private void syncHoldings(BrokerAccountEntity account, HyphenApiClient.HyphenCredential credential) {
        try {
            JsonNode holdingsData = apiClient.fetchHoldings(
                    credential, account.getBrokerName(), account.getAccountNumber());
            JsonNode data = extractDataNode(holdingsData);
            JsonNode accountNode = findHoldingsAccountNode(data, account.getAccountNumber());

            saveBalanceFromHoldings(account, accountNode);
            savePositionsFromHoldings(account, accountNode);

        } catch (ApiException ae) {
            throw ae;
        } catch (Exception e) {
            log.error("Error syncing holdings for account: {}", account.getId(), e);
            throw ApiException.internalServerError("잔고 동기화에 실패했습니다.", "HOLDINGS_SYNC_FAILED");
        }
    }

    private void syncCashBalance(BrokerAccountEntity account, HyphenApiClient.HyphenCredential credential) {
        try {
            JsonNode cashData = apiClient.fetchCashBalance(
                    credential, account.getBrokerName(), account.getAccountNumber());
            JsonNode data = extractDataNode(cashData);
            BigDecimal cashBalance = firstDecimal(data, "curBal");

            if (cashBalance.compareTo(BigDecimal.ZERO) == 0) {
                return;
            }

            accountBalanceRepository.findTopByAccountIdOrderByAsOfDateDesc(account.getId())
                    .ifPresentOrElse(balance -> {
                        balance.setCashBalance(cashBalance);
                        balance.setTotalAssetValue(balance.getEvaluationAmount().add(cashBalance));
                        balance.setLastSyncedAt(LocalDateTime.now());
                        accountBalanceRepository.save(balance);
                    }, () -> log.warn("No balance record to update cash for account: {}", account.getId()));

            log.info("Cash balance synced for account: {}", account.getId());
        } catch (Exception e) {
            log.error("Error syncing cash balance for account: {}", account.getId(), e);
        }
    }

    private void saveBalanceFromHoldings(BrokerAccountEntity account, JsonNode accountNode) {
        BigDecimal evaluationAmount = firstDecimal(accountNode, "totValuationAmt");
        BigDecimal depositAmount = firstDecimal(accountNode, "totPurchaseAmt");
        BigDecimal gainLoss = firstDecimal(accountNode, "totValuationGL");
        BigDecimal gainLossRate = firstDecimal(accountNode, "totProfitRate");
        BigDecimal estimatedDepositAsset = firstDecimal(accountNode, "estDepAsset");

        BigDecimal totalAssetValue = estimatedDepositAsset;
        if (totalAssetValue.compareTo(BigDecimal.ZERO) == 0) {
            totalAssetValue = evaluationAmount;
        }

        AccountBalanceEntity balance = AccountBalanceEntity.builder()
                .accountId(account.getId())
                .userId(account.getUserId())
                .totalAssetValue(totalAssetValue)
                .cashBalance(BigDecimal.ZERO)
                .depositAmount(depositAmount)
                .evaluationAmount(evaluationAmount)
                .gainLoss(gainLoss)
                .gainLossRate(gainLossRate)
                .dailyGainLoss(BigDecimal.ZERO)
                .dailyGainLossRate(BigDecimal.ZERO)
                .asOfDate(LocalDate.now())
                .lastSyncedAt(LocalDateTime.now())
                .build();

        accountBalanceRepository.save(balance);
        log.info("Holdings balance synced for account: {}", account.getId());
    }

    private void savePositionsFromHoldings(BrokerAccountEntity account, JsonNode accountNode) {
        JsonNode itemList = accountNode.path("itemList");
        assetPositionRepository.deleteByAccountId(account.getId());

        int savedCount = 0;
        if (itemList.isArray()) {
            for (JsonNode position : itemList) {
                String itemCode = textOr(position, "itemCd");
                if (itemCode == null || itemCode.isBlank()) {
                    continue;
                }

                AssetPositionEntity entity = AssetPositionEntity.builder()
                        .accountId(account.getId())
                        .userId(account.getUserId())
                        .symbol(itemCode)
                        .itemCode(itemCode)
                        .itemName(textOr(position, "itemNm"))
                        .positionType(defaultString(textOr(position, "productType"), "STOCK"))
                        .productCode(textOr(position, "productCd"))
                        .overseasYn(textOr(position, "exYn"))
                        .quantity(firstDecimal(position, "quantity"))
                        .purchasePrice(firstDecimal(position, "purchaseUnitPrice"))
                        .currentPrice(firstDecimal(position, "presentAmt"))
                        .currentValue(firstDecimal(position, "valuationAmt", "valuationAmt_KRW"))
                        .purchaseAmount(firstDecimal(position, "purchaseAmt", "purchaseAmt_KRW"))
                        .gainLoss(firstDecimal(position, "valuationGL"))
                        .gainLossRate(firstDecimal(position, "profitRate"))
                        .currencyCode(defaultString(textOr(position, "curCd"), "KRW"))
                        .lastSyncedAt(LocalDateTime.now())
                        .build();

                assetPositionRepository.save(entity);
                savedCount++;
            }
        }

        log.info("Positions synced for account: {}, count={}", account.getId(), savedCount);
    }

    private void syncDepositWithdrawHistory(BrokerAccountEntity account, HyphenApiClient.HyphenCredential credential) {
        try {
            String toDate = LocalDate.now().format(DATE_FORMAT);
            String fromDate = LocalDate.now().minusMonths(1).format(DATE_FORMAT);

            JsonNode historyData = apiClient.fetchDepositWithdrawHistory(
                    credential,
                    account.getBrokerName(),
                    account.getAccountNumber(),
                    fromDate,
                    toDate);

            JsonNode data = extractDataNode(historyData);
            JsonNode transactions = data.path("list");
            if (!transactions.isArray() || transactions.isEmpty()) {
                log.warn("Empty deposit/withdraw history for account: {}", account.getId());
                return;
            }

            log.info("Deposit/withdraw history synced for account: {}, count={}", account.getId(), transactions.size());
        } catch (Exception e) {
            log.error("Error syncing deposit/withdraw history for account: {}", account.getId(), e);
        }
    }

    private void syncAssetTransactionHistory(BrokerAccountEntity account, HyphenApiClient.HyphenCredential credential) {
        try {
            String toDate = LocalDate.now().format(DATE_FORMAT);
            String fromDate = LocalDate.now().minusMonths(1).format(DATE_FORMAT);

            JsonNode historyData = apiClient.fetchAssetTransactionHistory(
                    credential,
                    account.getBrokerName(),
                    account.getAccountNumber(),
                    fromDate,
                    toDate);

            JsonNode data = extractDataNode(historyData);
            JsonNode transactions = data.path("list");
            if (!transactions.isArray() || transactions.isEmpty()) {
                log.warn("Empty asset transaction history for account: {}", account.getId());
                return;
            }

            log.info("Asset transaction history synced for account: {}, count={}", account.getId(), transactions.size());
        } catch (Exception e) {
            log.error("Error syncing asset transaction history for account: {}", account.getId(), e);
        }
    }

    private JsonNode findHoldingsAccountNode(JsonNode data, String accountNumber) {
        JsonNode list = data.path("list");
        if (list.isArray()) {
            for (JsonNode node : list) {
                if (accountNumber != null && accountNumber.equals(node.path("acctNo").asText())) {
                    return node;
                }
            }
            if (!list.isEmpty()) {
                return list.get(0);
            }
        }
        return data;
    }

    @Transactional(readOnly = true)
    public BrokerAccountDto.SyncStatusResponse getSyncStatus(Long userId, Long accountId, Long syncId) {
        validateAccountAccess(userId, accountId);

        List<HyphenSyncHistoryEntity> histories = syncHistoryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);

        HyphenSyncHistoryEntity history = histories.stream()
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

    private JsonNode extractDataNode(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            throw ApiException.internalServerError("증권사 API 응답이 올바르지 않습니다.", "INVALID_API_RESPONSE");
        }
        return root.path("data");
    }

    private String decryptRequired(String encrypted, String errorCode) {
        if (encrypted == null || encrypted.isBlank()) {
            throw ApiException.badRequest("저장된 증권사 인증 정보가 없습니다.", errorCode);
        }
        return cryptoService.decrypt(encrypted);
    }

    private String decryptOptional(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            return "";
        }
        return cryptoService.decrypt(encrypted);
    }

    private static String textOr(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String defaultString(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static BigDecimal firstDecimal(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText("").trim();
                if (!text.isEmpty()) {
                    try {
                        return new BigDecimal(text.replace(",", ""));
                    } catch (NumberFormatException ignored) {
                        // try next key
                    }
                }
            }
        }
        return BigDecimal.ZERO;
    }
}
