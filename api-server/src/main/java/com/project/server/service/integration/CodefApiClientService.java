package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CodefApiClientService {

  private final ObjectMapper objectMapper;

  @Value("${codef.mode:test}")
  private String codefMode;

  @Value("${codef.test.base-url}")
  private String testBaseUrl;

  @Value("${codef.demo.base-url}")
  private String demoBaseUrl;

  @Value("${codef.prod.base-url}")
  private String prodBaseUrl;

  @Value("${codef.client-id:}")
  private String clientId;

  String tokenUrl = "https://oauth.codef.io/oauth/token";

  @Value("${codef.client-secret:}")
  private String clientSecret;
  @Value("${codef.api.timeout-seconds:12}")
  private long apiTimeoutSeconds;

  @Value("${codef.api.max-retries:3}")
  private int maxRetries;

  // Cached admin (client_credentials) token
  private volatile String cachedAdminToken;
  private volatile long cachedAdminTokenExpiryEpochSec = 0L;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3))
      .build();

  /**
   * CODEF OAuth 인증 URL 생성
   */
  public String generateAuthUrl(String state) {
    ensureCredentialsConfigured();
    String baseUrl = getBaseUrl();
    String clientId = getClientId();
    return String.format(
        "%s/oauth/authorize?client_id=%s&response_type=code&state=%s&scope=account.balance,account.transaction",
        baseUrl, clientId, state);
  }

  /**
   * OAuth 콜백에서 받은 코드로 토큰 요청
   */
  public Map<String, Object> requestAccessToken(String code) {
    try {
      ensureCredentialsConfigured();
      String clientId = getClientId();
      String clientSecret = getClientSecret();

      Map<String, String> body = new HashMap<>();
      body.put("grant_type", "authorization_code");
      body.put("code", code);
      body.put("client_id", clientId);
      body.put("client_secret", clientSecret);

      String requestBody = objectMapper.writeValueAsString(body);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(tokenUrl))
          .timeout(Duration.ofSeconds(apiTimeoutSeconds))
          .header("Content-Type", "application/json")
          .header("Accept", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
          .build();

      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

      if (response.statusCode() != 200) {
        log.error("CODEF token request failed: {}", response.body());
        throw ApiException.internalServerError("CODEF 인증 실패", "CODEF_AUTH_FAILED");
      }

      JsonNode result = objectMapper.readTree(response.body());
      Map<String, Object> tokenMap = new HashMap<>();
      tokenMap.put("accessToken", result.path("access_token").asText());
      tokenMap.put("tokenType", result.path("token_type").asText());
      tokenMap.put("expiresIn", result.path("expires_in").asInt());
      tokenMap.put("refreshToken", result.path("refresh_token").asText());
      // connectedId may be returned in some CODEF responses
      String connectedId = null;
      if (result.has("connectedId")) {
        connectedId = result.path("connectedId").asText(null);
      } else if (result.has("connected_id")) {
        connectedId = result.path("connected_id").asText(null);
      }
      tokenMap.put("connectedId", connectedId);

      return tokenMap;
    } catch (ApiException ae) {
      throw ae;
    } catch (Exception e) {
      log.error("Error requesting CODEF access token", e);
      throw ApiException.internalServerError("토큰 요청 실패", "CODEF_TOKEN_REQUEST_ERROR");
    }
  }

  /**
   * CODEF API 호출: 계좌 목록 조회
   */
  public JsonNode fetchAccountList(String accessToken) {
    return callCodefApi("/oauth/account/list", accessToken, HttpRequest.BodyPublishers.noBody(), "GET");
  }

  /**
   * CODEF 스크래핑 방식: adminToken + connectedId로 계좌 목록 조회
   */
  public JsonNode fetchAccountList(String adminToken, String connectedId) {
    Map<String, Object> params = new HashMap<>();
    // organization code for KIS (한국투자증권) = 0243
    params.put("organization", "0243");
    params.put("connectedId", connectedId);

    return callCodefApi("/v1/kr/stock/a/account/account-list", adminToken, params, "POST");
  }

  /**
   * CODEF API 호출: 계좌 잔액 조회
   */
  public JsonNode fetchAccountBalance(String accessToken, String accountNumber) {
    Map<String, String> params = new HashMap<>();
    params.put("accountNumber", accountNumber);

    return callCodefApi("/oauth/account/balance", accessToken, params, "POST");
  }

  public JsonNode fetchAccountBalance(String adminToken, String connectedId, String accountNumber) {
    Map<String, Object> params = new HashMap<>();
    params.put("organization", "0243");
    params.put("connectedId", connectedId);
    params.put("accountNumber", accountNumber);

    return callCodefApi("/v1/kr/stock/a/account/balance", adminToken, params, "POST");
  }

  /**
   * CODEF API 호출: 보유 자산 조회
   */
  public JsonNode fetchHoldingAssets(String accessToken, String accountNumber) {
    Map<String, String> params = new HashMap<>();
    params.put("accountNumber", accountNumber);

    return callCodefApi("/oauth/account/holding", accessToken, params, "POST");
  }

  public JsonNode fetchHoldingAssets(String adminToken, String connectedId, String accountNumber) {
    Map<String, Object> params = new HashMap<>();
    params.put("organization", "0243");
    params.put("connectedId", connectedId);
    params.put("accountNumber", accountNumber);

    return callCodefApi("/v1/kr/stock/a/account/assets", adminToken, params, "POST");
  }

  /**
   * CODEF API 호출: 거래 내역 조회
   */
  public JsonNode fetchTransactionHistory(String accessToken, String accountNumber, String fromDate, String toDate) {
    Map<String, String> params = new HashMap<>();
    params.put("accountNumber", accountNumber);
    params.put("fromDate", fromDate);
    params.put("toDate", toDate);

    return callCodefApi("/oauth/account/transaction", accessToken, params, "POST");
  }

  public JsonNode fetchTransactionHistory(String adminToken, String connectedId, String accountNumber, String fromDate,
      String toDate) {
    Map<String, Object> params = new HashMap<>();
    params.put("organization", "0243");
    params.put("connectedId", connectedId);
    params.put("accountNumber", accountNumber);
    params.put("fromDate", fromDate);
    params.put("toDate", toDate);

    return callCodefApi("/v1/kr/stock/a/account/transaction", adminToken, params, "POST");
  }

  /**
   * Generic CODEF API 호출 메서드
   */
  private JsonNode callCodefApi(String endpoint, String authToken, Object body, String method) {
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        String fullUrl = getBaseUrl() + endpoint;
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(fullUrl))
            .timeout(Duration.ofSeconds(apiTimeoutSeconds))
            .header("Authorization", "Bearer " + authToken)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json");

        String bodyString = "";
        if (body instanceof Map) {
          bodyString = objectMapper.writeValueAsString(body);
        }

        if ("POST".equals(method)) {
          if (bodyString.isEmpty()) {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString("{}"));
          } else {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8));
          }
        } else {
          requestBuilder.GET();
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() == 200) {
          return objectMapper.readTree(response.body());
        } else if (response.statusCode() == 401) {
          log.warn("CODEF token expired on attempt {}", attempt);
          throw ApiException.badRequest("CODEF 인증 토큰이 만료되었습니다.", "CODEF_TOKEN_EXPIRED");
        } else if (response.statusCode() >= 500) {
          if (attempt < maxRetries) {
            log.warn("CODEF server error on attempt {}, retrying...", attempt);
            Thread.sleep(1000 * attempt);
            continue;
          }
        }

        log.error("CODEF API error: {} - {}", response.statusCode(), response.body());
        throw ApiException.internalServerError("CODEF API 호출 실패", "CODEF_API_ERROR");

      } catch (ApiException ae) {
        throw ae;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw ApiException.internalServerError("API 요청 중단됨", "CODEF_INTERRUPTED");
      } catch (Exception e) {
        log.error("Error calling CODEF API: {}", endpoint, e);
        if (attempt == maxRetries) {
          throw ApiException.internalServerError("CODEF API 호출 실패", "CODEF_API_ERROR");
        }
      }
    }

    throw ApiException.internalServerError("CODEF API 호출 실패", "CODEF_API_ERROR");
  }

  /**
   * 발급받은 client_credentials 기반 admin 토큰을 반환 (캐시)
   */
  public String getAdminAccessToken() {
    try {
      long nowSec = System.currentTimeMillis() / 1000L;
      if (cachedAdminToken != null && nowSec < cachedAdminTokenExpiryEpochSec - 30) {
        return cachedAdminToken;
      }

      synchronized (this) {
        nowSec = System.currentTimeMillis() / 1000L;
        if (cachedAdminToken != null && nowSec < cachedAdminTokenExpiryEpochSec - 30) {
          return cachedAdminToken;
        }

        ensureCredentialsConfigured();

        String auth = getClientId() + ":" + getClientSecret();
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        // 3. Body 생성 (JSON이 아니라 x-www-form-urlencoded 형식)
        String requestBody = "grant_type=client_credentials&scope=read";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .timeout(Duration.ofSeconds(apiTimeoutSeconds))
            .header("Content-Type", "application/x-www-form-urlencoded") // 헤더 변경
            .header("Authorization", "Basic " + encodedAuth) // 인증 헤더 추가
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
          log.error("CODEF admin token request failed: {}", response.body());
          throw ApiException.internalServerError("CODEF admin 인증 실패", "CODEF_ADMIN_AUTH_FAILED");
        }

        JsonNode result = objectMapper.readTree(response.body());
        String token = result.path("access_token").asText(null);
        int expiresIn = result.path("expires_in").asInt(300);

        if (token == null) {
          throw ApiException.internalServerError("CODEF admin 토큰 응답이 유효하지 않습니다.", "CODEF_ADMIN_TOKEN_INVALID");
        }

        cachedAdminToken = token;
        cachedAdminTokenExpiryEpochSec = System.currentTimeMillis() / 1000L + expiresIn;
        return cachedAdminToken;
      }
    } catch (ApiException ae) {
      throw ae;
    } catch (Exception e) {
      log.error("Error requesting CODEF admin access token", e);
      throw ApiException.internalServerError("CODEF admin 토큰 요청 실패", "CODEF_ADMIN_TOKEN_ERROR");
    }
  }

  private String getBaseUrl() {
    return switch (normalizeMode()) {
      case "test" -> testBaseUrl;
      case "demo" -> demoBaseUrl;
      case "prod" -> prodBaseUrl;
      default -> throw ApiException.badRequest("지원하지 않는 CODEF 모드입니다.", "CODEF_INVALID_MODE");
    };
  }

  private String getClientId() {
    return clientId;
  }

  private String getClientSecret() {
    return clientSecret;
  }

  private void ensureCredentialsConfigured() {
    String clientId = getClientId();
    String clientSecret = getClientSecret();

    if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
      throw ApiException.internalServerError(
          "CODEF 인증 정보가 설정되지 않았습니다. 환경 변수 CODEF_CLIENT_ID/SECRET 를 확인하세요.",
          "CODEF_CONFIG_MISSING");
    }
  }

  private String normalizeMode() {
    return codefMode == null ? "test" : codefMode.trim().toLowerCase();
  }
}
