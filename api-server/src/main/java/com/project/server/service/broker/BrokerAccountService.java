package com.project.server.service.broker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.service.integration.CodefApiClientService;
import com.project.server.service.security.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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
  private final CodefApiClientService codefApiClientService;
  private final CryptoService cryptoService;
  private final ObjectMapper objectMapper;

  /**
   * 최초 계좌 연동: 프론트가 전달한 connectedId를 저장하고 다중 증권사 계좌 연동
   */
  public List<BrokerAccountDto.BrokerAccountResponse> initialLink(Long userId,
      BrokerAccountDto.LinkRequest request) {
    if (userId == null || userId <= 0) {
      throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
    }
    if (request.getConnectedId() == null || request.getConnectedId().isEmpty()) {
      throw ApiException.badRequest("connectedId가 필요합니다.", "MISSING_CONNECTED_ID");
    }
    if (request.getBrokerNames() == null || request.getBrokerNames().isEmpty()) {
      throw ApiException.badRequest("연동할 증권사 목록이 필요합니다.", "MISSING_BROKERS");
    }

    return linkAccountsForBrokers(userId, request.getConnectedId(), request.getBrokerNames());
  }

  /**
   * 추가 계좌 연동: DB에 저장된 connectedId를 사용하여 다중 증권사 계좌 연동
   */
  public List<BrokerAccountDto.BrokerAccountResponse> addLink(Long userId,
      BrokerAccountDto.LinkRequest request) {
    if (userId == null || userId <= 0) {
      throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
    }
    if (request.getBrokerNames() == null || request.getBrokerNames().isEmpty()) {
      throw ApiException.badRequest("연동할 증권사 목록이 필요합니다.", "MISSING_BROKERS");
    }

    // 기존 계좌에서 connectedId 추출 (복호화)
    String connectedId = brokerAccountRepository.findByUserId(userId).stream()
        .map(BrokerAccountEntity::getConnectedId)
        .filter(id -> id != null && !id.isEmpty())
        .findFirst()
        .map(cryptoService::decrypt)
        .orElseThrow(() -> ApiException.badRequest("저장된 간편인증 정보가 없습니다. 최초 연동을 먼저 진행해주세요.", "NO_CONNECTED_ID"));

    return linkAccountsForBrokers(userId, connectedId, request.getBrokerNames());
  }

  private List<BrokerAccountDto.BrokerAccountResponse> linkAccountsForBrokers(Long userId, String connectedId,
      List<String> brokerNames) {
    List<BrokerAccountDto.BrokerAccountResponse> linkedAccounts = new ArrayList<>();
    int totalAccountsFound = 0;
    int alreadyLinkedCount = 0;

    try {
      String accessToken = codefApiClientService.getAccessToken();

      for (String brokerName : brokerNames) {
        try {
          var accountList = codefApiClientService.fetchAccountList(accessToken, connectedId, brokerName);
          if (accountList == null || accountList.path("result").path("code").isMissingNode()) {
            log.warn("Failed to fetch accounts for broker: {}", brokerName);
            continue;
          }
          var accounts = accountList.path("data");
          if (accounts.isMissingNode() || !accounts.isArray() || accounts.isEmpty()) {
            log.warn("No accounts found for broker: {}", brokerName);
            continue;
          }

          for (com.fasterxml.jackson.databind.JsonNode accountNode : accounts) {
            totalAccountsFound++;
            String accountNumber = accountNode.path("resAccount").asText("");

            // 중복 연동 방지
            boolean alreadyLinked = brokerAccountRepository
                .findByUserIdAndBrokerNameAndAccountNumber(userId, brokerName.toUpperCase(), accountNumber)
                .isPresent();

            if (alreadyLinked) {
              alreadyLinkedCount++;
              continue;
            }

            String accountName = accountNode.path("resAccountName").asText("");
            String codefAccountDetailsJson = accountNode.toString();

            // 첫 연동이면 Primary로 설정
            boolean isPrimary = brokerAccountRepository.findByUserId(userId).isEmpty() && linkedAccounts.isEmpty();

            BrokerAccountEntity account = BrokerAccountEntity.builder()
                .userId(userId)
                .brokerName(brokerName.toUpperCase())
                .accountNumber(accountNumber)
                .accountNickname(accountName)
                .accountOwnerName(accountName)
                .accountType("STOCK")
                .codefStatus(BrokerAccountEntity.CodefStatus.CONNECTED)
                .isPrimary(isPrimary)
                .syncCount(0)
                .connectedId(cryptoService.encrypt(connectedId))
                .codefAccountDetails(codefAccountDetailsJson)
                .build();

            brokerAccountRepository.save(account);
            linkedAccounts.add(toResponse(account));
            log.info("Direct linked broker account: userId={}, broker={}, accountNumber={}", userId, brokerName,
                accountNumber);
          }
        } catch (Exception e) {
          log.error("Error linking broker: {}", brokerName, e);
        }
      }
    } catch (ApiException ae) {
      throw ae;
    } catch (Exception e) {
      log.error("Error during direct linking", e);
      throw ApiException.internalServerError("직접 연동 중 오류가 발생했습니다.", "DIRECT_LINKING_ERROR");
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

  /**
   * 사용자의 모든 연동 계좌 조회
   */
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

  /**
   * 특정 계좌 조회
   */
  @Transactional(readOnly = true)
  public BrokerAccountDto.BrokerAccountDetailResponse getAccount(Long userId, Long accountId) {
    BrokerAccountEntity account = validateAccountAccess(userId, accountId);

    return toDetailResponse(account);
  }

  /**
   * 계좌 연동 해제
   */
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

  /**
   * Primary 계좌 설정
   */
  public BrokerAccountDto.SetPrimaryAccountResponse setPrimaryAccount(Long userId, Long accountId) {
    BrokerAccountEntity account = validateAccountAccess(userId, accountId);

    // 기존 primary 계좌 해제
    BrokerAccountDto.SimpleAccountInfo previousPrimaryAccount = null;

    java.util.Optional<BrokerAccountEntity> prevPrimary = brokerAccountRepository.findByUserIdAndIsPrimary(userId,
        true);
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

  /**
   * 계좌 유효성 검증 및 조회 공통 로직
   */
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

  /**
   * Entity를 DTO로 변환
   */
  private BrokerAccountDto.BrokerAccountResponse toResponse(BrokerAccountEntity entity) {
    return BrokerAccountDto.BrokerAccountResponse.builder()
        .accountId(entity.getId())
        .brokerName(entity.getBrokerName())
        .accountNumber(entity.getAccountNumber())
        .accountNickname(entity.getAccountNickname())
        .accountOwnerName(entity.getAccountOwnerName())
        .status(entity.getCodefStatus().name())
        .isPrimary(entity.getIsPrimary())
        .lastSyncedAt(entity.getLastSyncedAt())
        .syncCount(entity.getSyncCount())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  /**
   * Entity를 Detail DTO로 변환 (계좌 정보 및 CODEF 응답 전체 포함)
   */
  private BrokerAccountDto.BrokerAccountDetailResponse toDetailResponse(BrokerAccountEntity entity) {
    Map<String, Object> accountDetails = new HashMap<>();
    if (entity.getCodefAccountDetails() != null && !entity.getCodefAccountDetails().isEmpty()) {
      try {
        accountDetails = objectMapper.readValue(
            entity.getCodefAccountDetails(),
            new TypeReference<Map<String, Object>>() {
            });
      } catch (Exception e) {
        log.warn("Failed to parse CODEF account details JSON for accountId={}", entity.getId(), e);
      }
    }

    return BrokerAccountDto.BrokerAccountDetailResponse.builder()
        .accountId(entity.getId())
        .brokerName(entity.getBrokerName())
        .accountNumber(entity.getAccountNumber())
        .accountNickname(entity.getAccountNickname())
        .accountOwnerName(entity.getAccountOwnerName())
        .accountType(entity.getAccountType())
        .status(entity.getCodefStatus().name())
        .isPrimary(entity.getIsPrimary())
        .lastSyncedAt(entity.getLastSyncedAt())
        .syncCount(entity.getSyncCount())
        .accountDisplay((String) accountDetails.get("resAccountDisplay"))
        .principal((String) accountDetails.get("resPrincipal"))
        .purchaseAmount((String) accountDetails.get("resPurchaseAmount"))
        .valuationAmt((String) accountDetails.get("resValuationAmt"))
        .valuationPL((String) accountDetails.get("resValuationPL"))
        .earningsRate((String) accountDetails.get("resEarningsRate"))
        .depositReceived((String) accountDetails.get("resDepositReceived"))
        .depositReceivedD1((String) accountDetails.get("resDepositReceivedD1"))
        .depositReceivedD2((String) accountDetails.get("resDepositReceivedD2"))
        .depositReceivedF((String) accountDetails.get("resDepositReceivedF"))
        .withdrawalAmt((String) accountDetails.get("resWithdrawalAmt"))
        .loanAmt((String) accountDetails.get("resLoanAmt"))
        .build();
  }
}
