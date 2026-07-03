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
  private final HyphenApiClient apiClient;
  private final CryptoService cryptoService;
  private final ObjectMapper objectMapper;

  public List<BrokerAccountDto.BrokerAccountResponse> initialLink(Long userId, BrokerAccountDto.LinkRequest request) {
    validateUserAndBrokers(userId, request);

    String loginUserId = resolveUserId(request);
    String userPw = request.getUserPw();
    if (userPw == null || userPw.isBlank()) {
      throw ApiException.badRequest("userPw가 필요합니다.", "MISSING_USER_PW");
    }

    String loginMethod = defaultString(request.getLoginMethod(), "ID");
    String loginRequired = defaultString(request.getLoginRequired(), "N");
    String accountPassword = defaultString(request.getAccountPassword(), "");

    return linkAccountsForBrokers(userId, loginUserId, userPw, loginMethod, loginRequired, accountPassword,
        request.getBrokerNames());
  }

  public List<BrokerAccountDto.BrokerAccountResponse> addLink(Long userId, BrokerAccountDto.LinkRequest request) {
    validateUserAndBrokers(userId, request);

    BrokerAccountEntity anchor = brokerAccountRepository.findByUserId(userId).stream()
        .findFirst()
        .orElseThrow(() -> ApiException.badRequest("저장된 증권사 연동 정보가 없습니다. 최초 연동을 먼저 진행해주세요.", "NO_LINK_INFO"));

    String loginUserId = decryptRequired(anchor.getLoginUserId(), "NO_USER_ID");
    String userPw = decryptRequired(anchor.getUserPassword(), "NO_USER_PW");
    String accountPassword = decryptOptional(anchor.getAccountPassword());

    String loginMethod = defaultString(request.getLoginMethod(), "ID");
    String loginRequired = defaultString(request.getLoginRequired(), "N");

    return linkAccountsForBrokers(userId, loginUserId, userPw, loginMethod, loginRequired, accountPassword,
        request.getBrokerNames());
  }

  private List<BrokerAccountDto.BrokerAccountResponse> linkAccountsForBrokers(
      Long userId,
      String loginUserId,
      String userPw,
      String loginMethod,
      String loginRequired,
      String accountPassword,
      List<String> brokerNames) {
    List<BrokerAccountDto.BrokerAccountResponse> linkedAccounts = new ArrayList<>();
    int totalAccountsFound = 0;
    int alreadyLinkedCount = 0;

    HyphenApiClient.HyphenCredential credential = new HyphenApiClient.HyphenCredential(
        loginUserId,
        userPw,
        loginMethod,
        loginRequired,
        accountPassword);

    try {
      for (String brokerName : brokerNames) {
        try {
          JsonNode accountList = apiClient.fetchAccountList(credential, brokerName);
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
            String accountDetailsJson = accountNode.toString();
            boolean isPrimary = brokerAccountRepository.findByUserId(userId).isEmpty() && linkedAccounts.isEmpty();

            BrokerAccountEntity account = BrokerAccountEntity.builder()
                .userId(userId)
                .brokerName(brokerName.toUpperCase())
                .accountNumber(accountNumber)
                .accountNickname(accountName)
                .accountOwnerName(accountOwner)
                .accountType(accountType != null ? accountType : "UNKNOWN")
                .connectionStatus(BrokerAccountEntity.ConnectionStatus.CONNECTED)
                .isPrimary(isPrimary)
                .syncCount(0)
                .loginUserId(cryptoService.encrypt(loginUserId))
                .userPassword(cryptoService.encrypt(userPw))
                .accountPassword((accountPassword == null || accountPassword.isBlank()) ? null : cryptoService.encrypt(accountPassword))
                .accountDetails(accountDetailsJson)
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
        .status(entity.getConnectionStatus() != null ? entity.getConnectionStatus().name() : BrokerAccountEntity.ConnectionStatus.PENDING.name())
        .isPrimary(entity.getIsPrimary())
        .account(parseAccountSnapshot(entity))
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
        .status(entity.getConnectionStatus() != null ? entity.getConnectionStatus().name() : BrokerAccountEntity.ConnectionStatus.PENDING.name())
        .isPrimary(entity.getIsPrimary())
        .account(parseAccountSnapshot(entity))
        .latestBalance(latestBalance)
        .positions(positions)
        .lastSyncedAt(entity.getLastSyncedAt())
        .syncCount(entity.getSyncCount())
        .build();
  }

  private BrokerAccountDto.AccountSnapshot parseAccountSnapshot(BrokerAccountEntity entity) {
    if (entity.getAccountDetails() == null || entity.getAccountDetails().isEmpty()) {
      return null;
    }
    try {
      Map<String, Object> accountDetails = objectMapper.readValue(
          entity.getAccountDetails(),
          new TypeReference<Map<String, Object>>() {
          });
      return HyphenFieldMapper.toAccountSnapshot(accountDetails);
    } catch (Exception e) {
      log.warn("Failed to parse account details JSON for accountId={}", entity.getId(), e);
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
  }

  private String resolveUserId(BrokerAccountDto.LinkRequest request) {
    String value = request.getUserId();
    if (value == null || value.isBlank()) {
      throw ApiException.badRequest("userId가 필요합니다.", "MISSING_USER_ID");
    }
    return value;
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
