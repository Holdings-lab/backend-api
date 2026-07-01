package com.project.server.service.broker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.AccountBalanceRepository;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.service.integration.HyphenApiClient;
import com.project.server.service.security.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class BrokerAccountService {

  private final BrokerAccountRepository brokerAccountRepository;
  private final AccountBalanceRepository accountBalanceRepository;
  private final AssetPositionRepository assetPositionRepository;
  private final HyphenApiClient hyphenApiClient;
  private final CryptoService cryptoService;
  private final ObjectMapper objectMapper;

  public List<BrokerAccountDto.BrokerAccountResponse> initialLink(Long userId, BrokerAccountDto.LinkRequest request) {
    validateUserAndBrokers(userId, request);

    String hyphenUserId = resolveHyphenUserId(request);
    String hyphenUserPw = request.getHyphenUserPw();
    if (hyphenUserPw == null || hyphenUserPw.isBlank()) {
      throw ApiException.badRequest("hyphenUserPw가 필요합니다.", "MISSING_HYPHEN_USER_PW");
    }

    String loginMethod = defaultString(request.getHyphenLoginMethod(), "ID");
    String loginRequired = defaultString(request.getHyphenLoginRequired(), "N");
    String accountPassword = defaultString(request.getHyphenAccountPassword(), "");

    return linkAccountsForBrokers(userId, hyphenUserId, hyphenUserPw, loginMethod, loginRequired, accountPassword,
        request.getBrokerNames());
  }

  public List<BrokerAccountDto.BrokerAccountResponse> addLink(Long userId, BrokerAccountDto.LinkRequest request) {
    validateUserAndBrokers(userId, request);

    BrokerAccountEntity anchor = brokerAccountRepository.findByUserId(userId).stream()
        .findFirst()
        .orElseThrow(() -> ApiException.badRequest("저장된 하이픈 연동 정보가 없습니다. 최초 연동을 먼저 진행해주세요.", "NO_HYPHEN_INFO"));

    String hyphenUserId = decryptRequired(anchor.getHyphenUserId(), "NO_HYPHEN_USER_ID");
    String hyphenUserPw = decryptRequired(anchor.getHyphenUserPassword(), "NO_HYPHEN_USER_PW");
    String accountPassword = decryptOptional(anchor.getHyphenAccountPassword());

    String loginMethod = defaultString(request.getHyphenLoginMethod(), "ID");
    String loginRequired = defaultString(request.getHyphenLoginRequired(), "N");

    return linkAccountsForBrokers(userId, hyphenUserId, hyphenUserPw, loginMethod, loginRequired, accountPassword,
        request.getBrokerNames());
  }

  private List<BrokerAccountDto.BrokerAccountResponse> linkAccountsForBrokers(
      Long userId,
      String hyphenUserId,
      String hyphenUserPw,
      String loginMethod,
      String loginRequired,
      String accountPassword,
      List<String> brokerNames) {
    List<BrokerAccountDto.BrokerAccountResponse> linkedAccounts = new ArrayList<>();
    int totalAccountsFound = 0;
    int alreadyLinkedCount = 0;

    HyphenApiClient.HyphenCredential credential = new HyphenApiClient.HyphenCredential(
        hyphenUserId,
        hyphenUserPw,
        loginMethod,
        loginRequired,
        accountPassword);

    try {
      for (String brokerName : brokerNames) {
        try {
          JsonNode accountList = hyphenApiClient.fetchAccountList(credential, brokerName);
          JsonNode accounts = accountList.path("data").path("list");
          if (!accounts.isArray() || accounts.isEmpty()) {
            log.warn("No accounts found for broker: {}", brokerName);
            continue;
          }

          for (JsonNode accountNode : accounts) {
            totalAccountsFound++;
            String accountNumber = textOr(accountNode, "acctNo");
            if (accountNumber == null || accountNumber.isBlank()) {
              continue;
            }

            boolean alreadyLinked = brokerAccountRepository
                .findByUserIdAndBrokerNameAndAccountNumber(userId, brokerName.toUpperCase(), accountNumber)
                .isPresent();
            if (alreadyLinked) {
              alreadyLinkedCount++;
              continue;
            }

            String accountName = textOr(accountNode, "acctNm", "acctNick", "acctHolder");
            String hyphenAccountDetailsJson = accountNode.toString();
            boolean isPrimary = brokerAccountRepository.findByUserId(userId).isEmpty() && linkedAccounts.isEmpty();

            BrokerAccountEntity account = BrokerAccountEntity.builder()
                .userId(userId)
                .brokerName(brokerName.toUpperCase())
                .accountNumber(accountNumber)
                .accountNickname(accountName)
                .accountOwnerName(accountName)
                .accountType("STOCK")
                .hyphenStatus(BrokerAccountEntity.HyphenStatus.CONNECTED)
                .isPrimary(isPrimary)
                .syncCount(0)
                .hyphenUserId(cryptoService.encrypt(hyphenUserId))
                .hyphenUserPassword(cryptoService.encrypt(hyphenUserPw))
                .hyphenAccountPassword((accountPassword == null || accountPassword.isBlank()) ? null : cryptoService.encrypt(accountPassword))
                .hyphenAccountDetails(hyphenAccountDetailsJson)
                .build();

            brokerAccountRepository.save(account);
            linkedAccounts.add(toResponse(account));
            log.info("Hyphen linked broker account: userId={}, broker={}, accountNumber={}", userId, brokerName,
                accountNumber);
          }
        } catch (Exception e) {
          log.error("Error linking broker via Hyphen: {}", brokerName, e);
        }
      }
    } catch (ApiException ae) {
      throw ae;
    } catch (Exception e) {
      log.error("Error during Hyphen linking", e);
      throw ApiException.internalServerError("하이픈 연동 중 오류가 발생했습니다.", "HYPHEN_LINKING_ERROR");
    }

    if (linkedAccounts.isEmpty()) {
      if (totalAccountsFound == 0) {
        throw ApiException.notFound("해당 증권사에서 연동 가능한 계좌를 찾지 못했습니다.", "NO_ACCOUNTS_FOUND");
      } else if (alreadyLinkedCount == totalAccountsFound) {
        throw ApiException.conflict("선택한 증권사의 계좌가 이미 모두 연동되어 있습니다.", "ALL_ACCOUNTS_ALREADY_LINKED");
      } else {
        throw ApiException.internalServerError("계좌 연동 중 알 수 없는 오류가 발생했습니다.", "LINKING_FAILED");
      }
    }

    return linkedAccounts;
  }

  @Transactional(readOnly = true)
  public List<BrokerAccountDto.BrokerAccountResponse> getUserAccounts(Long userId) {
    if (userId == null || userId <= 0) {
      throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
    }

    return brokerAccountRepository.findByUserId(userId)
        .stream()
        .map(this::toResponse)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public BrokerAccountDto.BrokerAccountDetailResponse getAccount(Long userId, Long accountId) {
    BrokerAccountEntity account = validateAccountAccess(userId, accountId);
    return toDetailResponse(account);
  }

  public BrokerAccountDto.UnlinkAccountResponse unlinkAccount(Long userId, Long accountId) {
    BrokerAccountEntity account = validateAccountAccess(userId, accountId);
    brokerAccountRepository.delete(account);

    log.info("Broker account unlinked: userId={}, accountId={}", userId, accountId);

    return BrokerAccountDto.UnlinkAccountResponse.builder()
        .accountId(account.getId())
        .brokerName(account.getBrokerName())
        .accountNumber(account.getAccountNumber() != null ? account.getAccountNumber() : "")
        .accountNickname(account.getAccountNickname() != null ? account.getAccountNickname() : "")
        .build();
  }

  public BrokerAccountDto.SetPrimaryAccountResponse setPrimaryAccount(Long userId, Long accountId) {
    BrokerAccountEntity account = validateAccountAccess(userId, accountId);

    BrokerAccountDto.SimpleAccountInfo previousPrimaryAccount = null;
    var prevPrimary = brokerAccountRepository.findByUserIdAndIsPrimary(userId, true);
    if (prevPrimary.isPresent()) {
      BrokerAccountEntity prevAccount = prevPrimary.get();
      prevAccount.setIsPrimary(false);
      brokerAccountRepository.save(prevAccount);

      previousPrimaryAccount = BrokerAccountDto.SimpleAccountInfo.builder()
          .accountId(prevAccount.getId())
          .brokerName(prevAccount.getBrokerName())
          .accountNumber(prevAccount.getAccountNumber())
          .accountNickname(prevAccount.getAccountNickname())
          .build();
    }

    account.setIsPrimary(true);
    brokerAccountRepository.save(account);

    log.info("Primary account set: userId={}, accountId={}", userId, accountId);

    return BrokerAccountDto.SetPrimaryAccountResponse.builder()
        .accountId(account.getId())
        .brokerName(account.getBrokerName())
        .accountNumber(account.getAccountNumber())
        .accountNickname(account.getAccountNickname())
        .previousPrimaryAccount(previousPrimaryAccount)
        .build();
  }

  public BrokerAccountEntity validateAccountAccess(Long userId, Long accountId) {
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

  private BrokerAccountDto.BrokerAccountResponse toResponse(BrokerAccountEntity entity) {
    return BrokerAccountDto.BrokerAccountResponse.builder()
        .accountId(entity.getId())
        .brokerName(entity.getBrokerName())
        .accountNumber(entity.getAccountNumber())
        .accountNickname(entity.getAccountNickname())
        .accountOwnerName(entity.getAccountOwnerName())
        .status(entity.getHyphenStatus() != null ? entity.getHyphenStatus().name() : BrokerAccountEntity.HyphenStatus.PENDING.name())
        .isPrimary(entity.getIsPrimary())
        .lastSyncedAt(entity.getLastSyncedAt())
        .syncCount(entity.getSyncCount())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  private BrokerAccountDto.BrokerAccountDetailResponse toDetailResponse(BrokerAccountEntity entity) {
    Map<String, Object> accountDetails = new HashMap<>();
    if (entity.getHyphenAccountDetails() != null && !entity.getHyphenAccountDetails().isEmpty()) {
      try {
        accountDetails = objectMapper.readValue(
            entity.getHyphenAccountDetails(),
            new TypeReference<Map<String, Object>>() {
            });
      } catch (Exception e) {
        log.warn("Failed to parse Hyphen account details JSON for accountId={}", entity.getId(), e);
      }
    }

    BrokerAccountDto.AccountBalanceDto latestBalance = accountBalanceRepository
        .findTopByAccountIdOrderByAsOfDateDesc(entity.getId())
        .map(balance -> BrokerAccountDto.AccountBalanceDto.builder()
            .id(balance.getId())
            .totalAssetValue(balance.getTotalAssetValue())
            .cashBalance(balance.getCashBalance())
            .depositAmount(balance.getDepositAmount())
            .evaluationAmount(balance.getEvaluationAmount())
            .gainLoss(balance.getGainLoss())
            .gainLossRate(balance.getGainLossRate())
            .dailyGainLoss(balance.getDailyGainLoss())
            .dailyGainLossRate(balance.getDailyGainLossRate())
            .asOfDate(balance.getAsOfDate())
            .lastSyncedAt(balance.getLastSyncedAt())
            .build())
        .orElse(null);

    List<BrokerAccountDto.AssetPositionDto> positions = assetPositionRepository.findByAccountId(entity.getId()).stream()
        .map(position -> BrokerAccountDto.AssetPositionDto.builder()
            .symbol(position.getSymbol())
            .positionType(position.getPositionType())
            .quantity(position.getQuantity())
            .purchasePrice(position.getPurchasePrice())
            .currentPrice(position.getCurrentPrice())
            .currentValue(position.getCurrentValue())
            .purchaseAmount(position.getPurchaseAmount())
            .gainLoss(position.getGainLoss())
            .gainLossRate(position.getGainLossRate())
            .currencyCode(position.getCurrencyCode())
            .purchasedAt(position.getPurchasedAt())
            .build())
        .collect(Collectors.toList());

    return BrokerAccountDto.BrokerAccountDetailResponse.builder()
        .accountId(entity.getId())
        .brokerName(entity.getBrokerName())
        .accountNumber(entity.getAccountNumber())
        .accountNickname(entity.getAccountNickname())
        .accountOwnerName(entity.getAccountOwnerName())
        .accountType(entity.getAccountType())
        .status(entity.getHyphenStatus() != null ? entity.getHyphenStatus().name() : BrokerAccountEntity.HyphenStatus.PENDING.name())
        .isPrimary(entity.getIsPrimary())
        .latestBalance(latestBalance)
        .positions(positions)
        .lastSyncedAt(entity.getLastSyncedAt())
        .syncCount(entity.getSyncCount())
        .accountDisplay(getString(accountDetails, "acctDisp"))
        .principal(getString(accountDetails, "totPurchaseAmt", "balance"))
        .purchaseAmount(getString(accountDetails, "totPurchaseAmt"))
        .valuationAmt(getString(accountDetails, "totValuationAmt"))
        .valuationPL(getString(accountDetails, "totValuationGL"))
        .earningsRate(getString(accountDetails, "totProfitRate"))
        .depositReceived(getString(accountDetails, "estDepAsset", "balance"))
        .depositReceivedD1(getString(accountDetails, "depositReceivedD1"))
        .depositReceivedD2(getString(accountDetails, "depositReceivedD2"))
        .depositReceivedF(getString(accountDetails, "depositReceivedF"))
        .withdrawalAmt(getString(accountDetails, "withdrawalAmt"))
        .loanAmt(getString(accountDetails, "loanAmt"))
        .build();
  }

  private static String getString(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object value = map.get(key);
      if (value != null && !String.valueOf(value).isBlank()) {
        return String.valueOf(value);
      }
    }
    return null;
  }

  private void validateUserAndBrokers(Long userId, BrokerAccountDto.LinkRequest request) {
    if (userId == null || userId <= 0) {
      throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
    }
    if (request.getBrokerNames() == null || request.getBrokerNames().isEmpty()) {
      throw ApiException.badRequest("연동할 증권사 목록이 필요합니다.", "MISSING_BROKERS");
    }
  }

  private String resolveHyphenUserId(BrokerAccountDto.LinkRequest request) {
    String value = request.getHyphenUserId();
    if (value == null || value.isBlank()) {
      throw ApiException.badRequest("hyphenUserId가 필요합니다.", "MISSING_HYPHEN_USER_ID");
    }
    return value;
  }

  private String decryptRequired(String encrypted, String errorCode) {
    if (encrypted == null || encrypted.isBlank()) {
      throw ApiException.badRequest("저장된 하이픈 인증 정보가 없습니다.", errorCode);
    }
    return cryptoService.decrypt(encrypted);
  }

  private String decryptOptional(String encrypted) {
    if (encrypted == null || encrypted.isBlank()) {
      return "";
    }
    return cryptoService.decrypt(encrypted);
  }

  private static String defaultString(String value, String fallback) {
    return (value == null || value.isBlank()) ? fallback : value;
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
}
