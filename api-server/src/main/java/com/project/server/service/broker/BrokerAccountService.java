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

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
   * OAuth 연동 시작 - 인증 URL 생성
   */
  public BrokerAccountDto.AuthResponse startOAuthLinking(Long userId, BrokerAccountDto.LinkRequest request) {
    if (userId == null || userId <= 0) {
      throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
    }

    if (!"KIS".equals(request.getBrokerName())) {
      throw ApiException.badRequest("현재 지원하는 증권사는 KIS(한국투자증권)입니다.", "UNSUPPORTED_BROKER");
    }

    // 이미 연동된 계좌가 있으면 상태 체크
    if (request.getAccountNumber() != null) {
      boolean alreadyLinked = brokerAccountRepository
          .findByUserIdAndBrokerNameAndAccountNumber(userId, request.getBrokerName(), request.getAccountNumber())
          .isPresent();
      if (alreadyLinked) {
        throw ApiException.badRequest("이미 연동된 계좌입니다.", "ACCOUNT_ALREADY_LINKED");
      }
    }

    // OAuth 상태 코드 생성 (userId를 인코딩하여 저장)
    String state = UUID.randomUUID().toString();
    // 여기서는 state를 메모리나 Redis에 저장해야 하지만, 간단히 진행
    // 실제 구현에서는 RedisTemplate이나 별도 캐시 사용

    String authUrl = codefApiClientService.generateAuthUrl(state);

    return BrokerAccountDto.AuthResponse.builder()
        .authUrl(authUrl)
        .build();
  }

  /**
   * OAuth 콜백 처리 - 토큰 발급 및 계좌 저장
   */
  public BrokerAccountDto.BrokerAccountResponse handleAuthCallback(Long userId,
      BrokerAccountDto.AuthCallbackRequest request) {
    if (userId == null || userId <= 0) {
      throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
    }

    if (request.getCode() == null || request.getCode().isEmpty()) {
      throw ApiException.badRequest("인증 코드가 없습니다.", "MISSING_AUTH_CODE");
    }

    try {
      // CODEF에서 토큰 요청
      Map<String, Object> tokenMap = codefApiClientService.requestAccessToken(request.getCode());
      String accessToken = (String) tokenMap.get("accessToken");
      String refreshToken = (String) tokenMap.get("refreshToken");
      String connectedId = (String) tokenMap.get("connectedId");

      // admin token을 발급받아 connectedId 기반으로 계좌 조회 시도
      String adminToken = codefApiClientService.getAdminAccessToken();
      var accountList = (connectedId != null)
          ? codefApiClientService.fetchAccountList(adminToken, connectedId)
          : codefApiClientService.fetchAccountList(accessToken);

      if (accountList == null || !accountList.has("result")) {
        throw ApiException.internalServerError("계좌 조회 실패", "ACCOUNT_FETCH_FAILED");
      }

      // 첫 번째 계좌 정보 저장 (실제로는 사용자가 선택하도록 할 수 있음)
      var accounts = accountList.get("result").path("data").path("accounts");
      if (accounts.isEmpty()) {
        throw ApiException.notFound("연동 가능한 계좌가 없습니다.", "NO_ACCOUNTS_FOUND");
      }

      var firstAccount = accounts.get(0);
      // CODEF 응답 전체를 JSON 문자열로 저장
      String codefAccountDetailsJson = firstAccount.toString();

      String accountNumber = firstAccount.path("resAccount").asText("");
      String accountName = firstAccount.path("resAccountName").asText("");
      String accountType = "STOCK";

      BrokerAccountEntity.BrokerAccountEntityBuilder builder = BrokerAccountEntity.builder()
          .userId(userId)
          .brokerName("KIS")
          .accountNumber(accountNumber)
          .accountNickname(accountName)
          .accountOwnerName(accountName)
          .accountType(accountType)
          .codefTokenId(accessToken)
          .codefTokenSecret(encryptToken(refreshToken))
          .codefStatus(BrokerAccountEntity.CodefStatus.CONNECTED)
          .isPrimary(true)
          .syncCount(0)
          .codefAccountDetails(codefAccountDetailsJson);

      if (connectedId != null) {
        builder.connectedId(connectedId);
      }

      BrokerAccountEntity account = builder.build();
      brokerAccountRepository.save(account);

      log.info("Broker account linked: userId={}, accountNumber={}", userId, accountNumber);

      return toResponse(account);

    } catch (ApiException ae) {
      throw ae;
    } catch (Exception e) {
      log.error("Error handling CODEF auth callback", e);
      throw ApiException.internalServerError("계좌 연동 중 오류가 발생했습니다.", "ACCOUNT_LINKING_ERROR");
    }
  }

  /**
   * 간편인증(Direct)을 통해 프론트가 전달한 connectedId로 바로 계좌 목록을 조회하고 연동
   */
  public BrokerAccountDto.BrokerAccountResponse directLinkAccount(Long userId, BrokerAccountDto.LinkRequest request) {
    if (userId == null || userId <= 0) {
      throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
    }

    if (!"KIS".equals(request.getBrokerName())) {
      throw ApiException.badRequest("현재 지원하는 증권사는 KIS(한국투자증권)입니다.", "UNSUPPORTED_BROKER");
    }

    if (request.getConnectedId() == null || request.getConnectedId().isEmpty()) {
      throw ApiException.badRequest("connectedId가 필요합니다.", "MISSING_CONNECTED_ID");
    }

    // 중복 체크: 프론트가 계좌번호를 전달하면 미리 검사
    if (request.getAccountNumber() != null) {
      boolean alreadyLinked = brokerAccountRepository
          .findByUserIdAndBrokerNameAndAccountNumber(userId, request.getBrokerName(), request.getAccountNumber())
          .isPresent();
      if (alreadyLinked) {
        throw ApiException.badRequest("이미 연동된 계좌입니다.", "ACCOUNT_ALREADY_LINKED");
      }
    }

    try {
      // 프론트에서 전달한 connectedId를 adminToken으로 계좌 조회
      String connectedId = request.getConnectedId();
      String adminToken = codefApiClientService.getAdminAccessToken();

      var accountList = codefApiClientService.fetchAccountList(adminToken, connectedId);
      log.info("CODEF Raw Response: {}", accountList.toString());

      if (accountList == null || accountList.path("result").path("code").isMissingNode()) {
        throw ApiException.internalServerError("계좌 조회 응답이 유효하지 않습니다.", "ACCOUNT_FETCH_FAILED");
      }

      var accounts = accountList.path("data");

      if (accounts.isMissingNode() || !accounts.isArray() || accounts.isEmpty()) {
        log.warn("No accounts in data array for connectedId: {}", connectedId);
        throw ApiException.notFound("연동 가능한 계좌가 없습니다.", "NO_ACCOUNTS_FOUND");
      }

      var firstAccount = accounts.get(0);
      // CODEF 응답 전체를 JSON 문자열로 저장
      String codefAccountDetailsJson = firstAccount.toString();

      String accountNumber = firstAccount.path("resAccount").asText("");
      String accountName = firstAccount.path("resAccountName").asText("");
      String accountType = "STOCK";

      // connectedId 기반 스크래핑 방식이므로 connectedId를 저장
      BrokerAccountEntity account = BrokerAccountEntity.builder()
          .userId(userId)
          .brokerName("KIS")
          .accountNumber(accountNumber)
          .accountNickname(accountName)
          .accountOwnerName(accountName)
          .accountType(accountType)
          .codefStatus(BrokerAccountEntity.CodefStatus.CONNECTED)
          .isPrimary(true)
          .syncCount(0)
          .connectedId(connectedId)
          .codefAccountDetails(codefAccountDetailsJson)
          .build();

      brokerAccountRepository.save(account);

      log.info("Direct linked broker account: userId={}, accountNumber={}", userId, accountNumber);

      return toResponse(account);

    } catch (ApiException ae) {
      throw ae;
    } catch (Exception e) {
      log.error("Error during direct linking", e);
      throw ApiException.internalServerError("직접 연동 중 오류가 발생했습니다.", "DIRECT_LINKING_ERROR");
    }
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
  public BrokerAccountDto.BrokerAccountDetailResponse getAccount(Long accountId) {
    // userId로 접근 권한 체크 로직 구현 필요 (본인의 자산인지)
    BrokerAccountEntity account = brokerAccountRepository.findById(accountId)
        .orElseThrow(() -> ApiException.notFound("계좌를 찾을 수 없습니다.", "ACCOUNT_NOT_FOUND"));

    return toDetailResponse(account);
  }

  /**
   * 계좌 연동 해제
   */
  public BrokerAccountDto.UnlinkAccountResponse unlinkAccount(Long userId, Long accountId) {
    BrokerAccountEntity account = brokerAccountRepository.findById(accountId)
        .orElseThrow(() -> ApiException.notFound("계좌를 찾을 수 없습니다.", "ACCOUNT_NOT_FOUND"));

    if (!account.getUserId().equals(userId)) {
      throw ApiException.badRequest("접근 권한이 없습니다.", "FORBIDDEN_ACCESS");
    }

    brokerAccountRepository.delete(account);

    log.info("Broker account unlinked: userId={}, accountId={}", userId, accountId);

    return BrokerAccountDto.UnlinkAccountResponse.builder()
        .brokerName(account.getBrokerName())
        .resAccountName(account.getAccountNickname() != null ? account.getAccountNickname() : "")
        .resAccount(account.getAccountNumber() != null ? account.getAccountNumber() : "")
        .build();
  }

  /**
   * 토큰 갱신
   */
  public void refreshToken(Long accountId) {
    BrokerAccountEntity account = brokerAccountRepository.findById(accountId)
        .orElseThrow(() -> ApiException.notFound("계좌를 찾을 수 없습니다.", "ACCOUNT_NOT_FOUND"));

    if (account.getCodefTokenSecret() == null) {
      if (account.getConnectedId() != null) {
        throw ApiException.badRequest("스크래핑 연결 방식은 리프레시 토큰이 없습니다.", "NO_REFRESH_TOKEN_SCRAPING");
      }
      throw ApiException.badRequest("갱신 토큰이 없습니다.", "NO_REFRESH_TOKEN");
    }

    try {
      // CODEF 토큰 갱신 로직 (실제 구현 필요)
      // Map<String, Object> newToken =
      // codefApiClientService.refreshAccessToken(decryptToken(account.getCodefTokenSecret()));

      account.setCodefStatus(BrokerAccountEntity.CodefStatus.CONNECTED);
      brokerAccountRepository.save(account);
    } catch (Exception e) {
      log.error("Error refreshing CODEF token for account: {}", accountId, e);
      account.setCodefStatus(BrokerAccountEntity.CodefStatus.EXPIRED);
      brokerAccountRepository.save(account);
      throw ApiException.internalServerError("토큰 갱신 실패", "TOKEN_REFRESH_ERROR");
    }
  }

  /**
   * Primary 계좌 설정
   */
  public void setPrimaryAccount(Long userId, Long accountId) {
    // 기존 primary 계좌 해제
    brokerAccountRepository.findByUserIdAndIsPrimary(userId, true)
        .ifPresent(account -> {
          account.setIsPrimary(false);
          brokerAccountRepository.save(account);
        });

    // 새 primary 계좌 설정
    BrokerAccountEntity account = brokerAccountRepository.findById(accountId)
        .orElseThrow(() -> ApiException.notFound("계좌를 찾을 수 없습니다.", "ACCOUNT_NOT_FOUND"));

    if (!account.getUserId().equals(userId)) {
      throw ApiException.badRequest("접근 권한이 없습니다.", "FORBIDDEN_ACCESS");
    }

    account.setIsPrimary(true);
    brokerAccountRepository.save(account);

    log.info("Primary account set: userId={}, accountId={}", userId, accountId);
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
            new TypeReference<Map<String, Object>>() {}
        );
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

  /**
   * 토큰 암호화 (AES-256)
   */
  private String encryptToken(String token) {
    return cryptoService.encrypt(token);
  }

  /**
   * 토큰 복호화 (AES-256)
   */
  private String decryptToken(String encryptedToken) {
    return cryptoService.decrypt(encryptedToken);
  }
}
