package com.project.server.service.broker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.broker.SupportedBroker;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.AccountBalanceRepository;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.service.integration.HyphenApiClient;
import com.project.server.service.onboarding.OnboardingService;
import com.project.server.service.security.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
  private final OnboardingService onboardingService;

  /**
   * 증권사 계좌 연동.
   * 하이픈은 요청마다 증권사 로그인 자격증명이 필요하므로, 최초/추가 연동을 구분하지 않는다.
   *
   * @param userId 앱 사용자 ID
   * @param request hyphenUserId/hyphenUserPw + brokerNames 필수
   */
  public List<BrokerAccountDto.BrokerAccountResponse> linkAccounts(Long userId, BrokerAccountDto.LinkRequest request) {
    validateUserAndBrokers(userId, request);

    String hyphenUserId = requireHyphenUserId(request);
    String hyphenUserPw = requireHyphenUserPw(request);
    String hyphenLoginMethod = defaultString(request.getHyphenLoginMethod(), "ID");
    String hyphenLoginRequired = defaultString(request.getHyphenLoginRequired(), "N");
    String hyphenAccountPassword = defaultString(request.getHyphenAccountPassword(), "");

    List<BrokerAccountDto.BrokerAccountResponse> linkedAccounts = new ArrayList<>();
    int totalAccountsFound = 0;
    int alreadyLinkedCount = 0;

    HyphenApiClient.HyphenCredential credential = new HyphenApiClient.HyphenCredential(
        hyphenUserId,
        hyphenUserPw,
        hyphenLoginMethod,
        hyphenLoginRequired,
        hyphenAccountPassword);

    try {
      for (String brokerName : request.getBrokerNames()) {
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

            String accountName = textOr(accountNode, "acctNick", "acctNm");
            String accountOwner = textOr(accountNode, "acctHolder", "acctNm");
            String accountType = textOr(accountNode, "acctNm");
            String hyphenAccountDetailsJson = accountNode.toString();
            boolean isPrimary = brokerAccountRepository.findByUserId(userId).isEmpty() && linkedAccounts.isEmpty();

            BrokerAccountEntity account = BrokerAccountEntity.builder()
                .userId(userId)
                .brokerName(brokerName.toUpperCase())
                .accountNumber(accountNumber)
                .accountNickname(accountName)
                .accountOwnerName(accountOwner)
                .accountType(accountType != null ? accountType : "UNKNOWN")
                .hyphenStatus(BrokerAccountEntity.HyphenStatus.CONNECTED)
                .isPrimary(isPrimary)
                .syncCount(0)
                .hyphenUserId(cryptoService.encrypt(hyphenUserId))
                .hyphenUserPassword(cryptoService.encrypt(hyphenUserPw))
                .hyphenAccountPassword(
                    (hyphenAccountPassword == null || hyphenAccountPassword.isBlank())
                        ? null
                        : cryptoService.encrypt(hyphenAccountPassword))
                .hyphenAccountDetails(hyphenAccountDetailsJson)
                .build();

            brokerAccountRepository.save(account);
            linkedAccounts.add(toResponse(account));
            log.info("Linked broker account: userId={}, broker={}, accountNumber={}", userId, brokerName,
                accountNumber);
          }
        } catch (Exception e) {
          log.error("Error linking broker: {}", brokerName, e);
        }
      }
    } catch (ApiException ae) {
      throw ae;
    } catch (Exception e) {
      log.error("Error during broker linking", e);
      throw ApiException.internalServerError("증권사 연동 중 오류가 발생했습니다.", "LINKING_ERROR");
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

    onboardingService.markAccountLinked(userId);
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
        .accountType(entity.getAccountType())
        .status(entity.getHyphenStatus() != null
            ? entity.getHyphenStatus().name()
            : BrokerAccountEntity.HyphenStatus.PENDING.name())
        .isPrimary(entity.getIsPrimary())
        .hyphenAccount(parseHyphenAccount(entity))
        .lastSyncedAt(entity.getLastSyncedAt())
        .syncCount(entity.getSyncCount())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  private BrokerAccountDto.BrokerAccountDetailResponse toDetailResponse(BrokerAccountEntity entity) {
    BrokerAccountDto.AccountBalanceDto latestBalance = accountBalanceRepository
        .findTopByAccountIdOrderByAsOfDateDesc(entity.getId())
        .map(HyphenFieldMapper::toBalanceDto)
        .orElse(null);

    List<BrokerAccountDto.AssetPositionDto> positions = assetPositionRepository.findByAccountId(entity.getId()).stream()
        .map(HyphenFieldMapper::toPositionDto)
        .collect(Collectors.toList());

    return BrokerAccountDto.BrokerAccountDetailResponse.builder()
        .accountId(entity.getId())
        .brokerName(entity.getBrokerName())
        .accountNumber(entity.getAccountNumber())
        .accountNickname(entity.getAccountNickname())
        .accountOwnerName(entity.getAccountOwnerName())
        .accountType(entity.getAccountType())
        .status(entity.getHyphenStatus() != null
            ? entity.getHyphenStatus().name()
            : BrokerAccountEntity.HyphenStatus.PENDING.name())
        .isPrimary(entity.getIsPrimary())
        .hyphenAccount(parseHyphenAccount(entity))
        .latestBalance(latestBalance)
        .positions(positions)
        .lastSyncedAt(entity.getLastSyncedAt())
        .syncCount(entity.getSyncCount())
        .build();
  }

  private BrokerAccountDto.HyphenAccountSnapshot parseHyphenAccount(BrokerAccountEntity entity) {
    if (entity.getHyphenAccountDetails() == null || entity.getHyphenAccountDetails().isEmpty()) {
      return null;
    }
    try {
      Map<String, Object> details = objectMapper.readValue(
          entity.getHyphenAccountDetails(),
          new TypeReference<Map<String, Object>>() {
          });
      return HyphenFieldMapper.toAccountSnapshot(details);
    } catch (Exception e) {
      log.warn("Failed to parse hyphen account details JSON for accountId={}", entity.getId(), e);
      return null;
    }
  }

  private void validateUserAndBrokers(Long userId, BrokerAccountDto.LinkRequest request) {
    if (userId == null || userId <= 0) {
      throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
    }
    if (request.getBrokerNames() == null || request.getBrokerNames().isEmpty()) {
      throw ApiException.badRequest("연동할 증권사 목록이 필요합니다.", "MISSING_BROKERS");
    }
    for (String brokerName : request.getBrokerNames()) {
      SupportedBroker broker = SupportedBroker.fromCode(brokerName)
          .orElseThrow(() -> ApiException.badRequest(
              "지원하지 않는 증권사입니다: " + brokerName, "UNSUPPORTED_BROKER"));
      if (!broker.available()) {
        throw ApiException.badRequest(
            broker.displayName() + " 연동 준비 중입니다.", "BROKER_NOT_AVAILABLE");
      }
    }
  }

  private String requireHyphenUserId(BrokerAccountDto.LinkRequest request) {
    String value = request.getHyphenUserId();
    if (value == null || value.isBlank()) {
      throw ApiException.badRequest("hyphenUserId(증권사 로그인 ID)가 필요합니다.", "MISSING_HYPHEN_USER_ID");
    }
    return value;
  }

  private String requireHyphenUserPw(BrokerAccountDto.LinkRequest request) {
    String value = request.getHyphenUserPw();
    if (value == null || value.isBlank()) {
      throw ApiException.badRequest("hyphenUserPw(증권사 로그인 비밀번호)가 필요합니다.", "MISSING_HYPHEN_USER_PW");
    }
    return value;
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
