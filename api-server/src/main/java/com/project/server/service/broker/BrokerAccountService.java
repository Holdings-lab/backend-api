package com.project.server.service.broker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.broker.SupportedBroker;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.AccountBalanceRepository;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.service.integration.kis.KisApiClient;
import com.project.server.service.integration.kis.KisCredentialResolver;
import com.project.server.service.integration.kis.KisFieldMapper;
import com.project.server.service.integration.kis.KisTokenService;
import com.project.server.service.onboarding.OnboardingService;
import com.project.server.service.security.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final KisApiClient kisApiClient;
  private final KisCredentialResolver kisCredentialResolver;
  private final KisTokenService kisTokenService;
  private final CryptoService cryptoService;
  private final ObjectMapper objectMapper;
  private final OnboardingService onboardingService;

  /**
   * 한투 계좌 연동.
   * appKey/appSecret이 없으면 KIS_MOCK_* env 계좌를 현재 사용자에게 바인딩한다.
   */
  public List<BrokerAccountDto.BrokerAccountResponse> linkAccounts(Long userId, BrokerAccountDto.LinkRequest request) {
    BrokerAccountDto.LinkRequest safe = request == null ? new BrokerAccountDto.LinkRequest() : request;
    String brokerName = resolveBrokerName(userId, safe);
    KisApiClient.KisCredential credential = kisCredentialResolver.fromLinkRequest(safe);
    String storedAccountNumber = credential.cano() + credential.accountProductCode();

    boolean alreadyLinked = brokerAccountRepository
        .findByUserIdAndBrokerNameAndAccountNumber(userId, brokerName, storedAccountNumber)
        .isPresent();
    if (alreadyLinked) {
      throw ApiException.conflict("해당 계좌가 이미 연동되어 있습니다.", "ALL_ACCOUNTS_ALREADY_LINKED");
    }

    KisApiClient.KisBalanceSnapshot snapshot = kisApiClient.fetchBalance(credential);

    boolean isPrimary = brokerAccountRepository.findByUserId(userId).isEmpty();
    BrokerAccountEntity.CredentialSource source =
        credential.source() == KisApiClient.BrokerAccountCredentialSource.USER
            ? BrokerAccountEntity.CredentialSource.USER
            : BrokerAccountEntity.CredentialSource.ENV;

    BrokerAccountEntity account = BrokerAccountEntity.builder()
        .userId(userId)
        .brokerName(brokerName)
        .accountNumber(storedAccountNumber)
        .accountNickname(snapshot.accountDisplay())
        .accountOwnerName(null)
        .accountType("KIS")
        .connectionStatus(BrokerAccountEntity.ConnectionStatus.CONNECTED)
        .isPrimary(isPrimary)
        .syncCount(0)
        .appKey(source == BrokerAccountEntity.CredentialSource.USER
            ? cryptoService.encrypt(credential.appKey())
            : null)
        .appSecret(source == BrokerAccountEntity.CredentialSource.USER
            ? cryptoService.encrypt(credential.appSecret())
            : null)
        .accountProductCode(credential.accountProductCode())
        .accountDetails(writeDetails(snapshot))
        .credentialSource(source)
        .build();

    brokerAccountRepository.save(account);
    onboardingService.markAccountLinked(userId);
    log.info("Linked KIS account: userId={}, accountNumber={}, source={}",
        userId, storedAccountNumber, source);
    return List.of(toResponse(account));
  }

  public BrokerAccountDto.BrokerAccountResponse updateCredentials(
      Long userId,
      Long accountId,
      BrokerAccountDto.CredentialsUpdateRequest request) {
    BrokerAccountEntity account = validateAccountAccess(userId, accountId);
    String previousAppKey = decryptOptional(account.getAppKey());
    KisApiClient.KisCredential credential = kisCredentialResolver.fromCredentialUpdate(account, request);
    kisApiClient.fetchBalance(credential);

    if (previousAppKey != null) {
      kisTokenService.invalidate(previousAppKey);
    }
    kisTokenService.invalidate(credential.appKey());

    String storedAccountNumber = credential.cano() + credential.accountProductCode();
    BrokerAccountEntity.CredentialSource source =
        credential.source() == KisApiClient.BrokerAccountCredentialSource.USER
            ? BrokerAccountEntity.CredentialSource.USER
            : BrokerAccountEntity.CredentialSource.ENV;

    account.setAccountNumber(storedAccountNumber);
    account.setAccountProductCode(credential.accountProductCode());
    account.setCredentialSource(source);
    account.setConnectionStatus(BrokerAccountEntity.ConnectionStatus.CONNECTED);
    if (source == BrokerAccountEntity.CredentialSource.USER) {
      account.setAppKey(cryptoService.encrypt(credential.appKey()));
      account.setAppSecret(cryptoService.encrypt(credential.appSecret()));
    } else {
      account.setAppKey(null);
      account.setAppSecret(null);
    }
    brokerAccountRepository.save(account);
    log.info("Updated KIS credentials: userId={}, accountId={}, source={}", userId, accountId, source);
    return toResponse(account);
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
    String previousAppKey = decryptOptional(account.getAppKey());
    if (previousAppKey != null) {
      kisTokenService.invalidate(previousAppKey);
    }
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
        .status(statusName(entity))
        .isPrimary(entity.getIsPrimary())
        .accountSnapshot(parseAccountSnapshot(entity))
        .credentialSource(entity.getCredentialSource() != null ? entity.getCredentialSource().name() : null)
        .hasCredentials(kisCredentialResolver.hasResolvableCredentials(entity))
        .lastSyncedAt(entity.getLastSyncedAt())
        .syncCount(entity.getSyncCount())
        .createdAt(entity.getCreatedAt())
        .build();
  }

  private BrokerAccountDto.BrokerAccountDetailResponse toDetailResponse(BrokerAccountEntity entity) {
    BrokerAccountDto.AccountBalanceDto latestBalance = accountBalanceRepository
        .findTopByAccountIdOrderByAsOfDateDesc(entity.getId())
        .map(BrokerFieldMapper::toBalanceDto)
        .orElse(null);

    List<BrokerAccountDto.AssetPositionDto> positions = assetPositionRepository.findByAccountId(entity.getId()).stream()
        .map(BrokerFieldMapper::toPositionDto)
        .collect(Collectors.toList());

    return BrokerAccountDto.BrokerAccountDetailResponse.builder()
        .accountId(entity.getId())
        .brokerName(entity.getBrokerName())
        .accountNumber(entity.getAccountNumber())
        .accountNickname(entity.getAccountNickname())
        .accountOwnerName(entity.getAccountOwnerName())
        .accountType(entity.getAccountType())
        .status(statusName(entity))
        .isPrimary(entity.getIsPrimary())
        .accountSnapshot(parseAccountSnapshot(entity))
        .credentialSource(entity.getCredentialSource() != null ? entity.getCredentialSource().name() : null)
        .hasCredentials(kisCredentialResolver.hasResolvableCredentials(entity))
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
      Map<String, Object> details = objectMapper.readValue(
          entity.getAccountDetails(),
          new TypeReference<Map<String, Object>>() {
          });
      return KisFieldMapper.toAccountSnapshot(entity, details);
    } catch (Exception e) {
      log.warn("Failed to parse account details JSON for accountId={}", entity.getId(), e);
      return null;
    }
  }

  private String writeDetails(KisApiClient.KisBalanceSnapshot snapshot) {
    try {
      return objectMapper.writeValueAsString(KisFieldMapper.toAccountDetails(snapshot));
    } catch (Exception e) {
      throw ApiException.internalServerError("계좌 스냅샷 저장에 실패했습니다.", "ACCOUNT_DETAILS_WRITE_FAILED");
    }
  }

  private String resolveBrokerName(Long userId, BrokerAccountDto.LinkRequest request) {
    if (userId == null || userId <= 0) {
      throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
    }
    String resolved = request.getBrokerName();
    if ((resolved == null || resolved.isBlank())
        && request.getBrokerNames() != null
        && !request.getBrokerNames().isEmpty()) {
      resolved = request.getBrokerNames().get(0);
    }
    if (resolved == null || resolved.isBlank()) {
      resolved = SupportedBroker.KIS.code();
    }
    final String brokerCode = resolved;
    SupportedBroker broker = SupportedBroker.fromCode(brokerCode)
        .orElseThrow(() -> ApiException.badRequest(
            "지원하지 않는 증권사입니다: " + brokerCode, "UNSUPPORTED_BROKER"));
    if (!broker.available()) {
      throw ApiException.badRequest(
          broker.displayName() + " 연동 준비 중입니다.", "BROKER_NOT_AVAILABLE");
    }
    return broker.code();
  }

  private static String statusName(BrokerAccountEntity entity) {
    return entity.getConnectionStatus() != null
        ? entity.getConnectionStatus().name()
        : BrokerAccountEntity.ConnectionStatus.PENDING.name();
  }

  private String decryptOptional(String encrypted) {
    if (encrypted == null || encrypted.isBlank()) {
      return null;
    }
    return cryptoService.decrypt(encrypted);
  }
}
