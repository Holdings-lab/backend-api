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

  @Value("${codef.sandbox.base-url}")
  private String sandboxBaseUrl;

  @Value("${codef.client-id:}")
  private String clientId;

  String tokenUrl = "https://oauth.codef.io/oauth/token";

  @Value("${codef.client-secret:}")
  private String clientSecret;
  @Value("${codef.api.timeout-seconds:12}")
  private long apiTimeoutSeconds;

  @Value("${codef.api.max-retries:3}")
  private int maxRetries;

  // Cached client_credentials token
  private volatile String cachedAccessToken;
  private volatile long cachedAccessTokenExpiryEpochSec = 0L;

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3))
      .build();

  public static final Map<String, String> BROKER_ORG_CODES = Map.of(
      "KIS", "0243",
      "NH", "0247",
      "KB", "0240",
      "MIRAE", "0238",
      "SAMSUNG", "0242",
      "KIWOOM", "0264");

  private String getOrgCode(String brokerName) {
    return BROKER_ORG_CODES.getOrDefault(brokerName.toUpperCase(), "0243");
  }

  /**
   * CODEF 스크래핑 방식: adminToken + connectedId로 계좌 목록 조회
   */
  public JsonNode fetchAccountList(String accessToken, String connectedId, String brokerName) {
    Map<String, Object> params = new HashMap<>();
    params.put("organization", getOrgCode(brokerName));
    params.put("connectedId", connectedId);

    return callCodefApi("/v1/kr/stock/a/account/account-list", accessToken, params, "POST");
  }

  public JsonNode fetchAccountBalance(String accessToken, String connectedId, String brokerName, String accountNumber) {
    Map<String, Object> params = new HashMap<>();
    params.put("organization", getOrgCode(brokerName));
    params.put("connectedId", connectedId);
    params.put("accountNumber", accountNumber);

    return callCodefApi("/v1/kr/stock/a/account/balance", accessToken, params, "POST");
  }

  public JsonNode fetchHoldingAssets(String accessToken, String connectedId, String brokerName, String accountNumber) {
    Map<String, Object> params = new HashMap<>();
    params.put("organization", getOrgCode(brokerName));
    params.put("connectedId", connectedId);
    params.put("accountNumber", accountNumber);

    return callCodefApi("/v1/kr/stock/a/account/assets", accessToken, params, "POST");
  }

  public JsonNode fetchTransactionHistory(String accessToken, String connectedId, String brokerName,
      String accountNumber, String fromDate,
      String toDate) {
    Map<String, Object> params = new HashMap<>();
    params.put("organization", getOrgCode(brokerName));
    params.put("connectedId", connectedId);
    params.put("accountNumber", accountNumber);
    params.put("fromDate", fromDate);
    params.put("toDate", toDate);

    return callCodefApi("/v1/kr/stock/a/account/transaction", accessToken, params, "POST");
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
   * 발급받은 client_credentials 기반 토큰을 반환 (캐시)
   */
  public String getAccessToken() {
    try {
      long nowSec = System.currentTimeMillis() / 1000L;
      // 토큰 만료 10분(600초) 전에 갱신하도록 설정
      if (cachedAccessToken != null && nowSec < cachedAccessTokenExpiryEpochSec - 600) {
        return cachedAccessToken;
      }

      synchronized (this) {
        nowSec = System.currentTimeMillis() / 1000L;
        if (cachedAccessToken != null && nowSec < cachedAccessTokenExpiryEpochSec - 600) {
          return cachedAccessToken;
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
          log.error("CODEF access token request failed: {}", response.body());
          throw ApiException.internalServerError("CODEF 인증 실패", "CODEF_AUTH_FAILED");
        }

        JsonNode root = objectMapper.readTree(response.body());
        String token = root.path("access_token").asText(null);
        long expiresIn = root.path("expires_in").asLong(0);

        if (token == null) {
          throw ApiException.internalServerError("CODEF 토큰 응답이 유효하지 않습니다.", "CODEF_TOKEN_INVALID");
        }

        cachedAccessToken = token;
        cachedAccessTokenExpiryEpochSec = System.currentTimeMillis() / 1000L + expiresIn;
        return cachedAccessToken;
      }
    } catch (ApiException ae) {
      throw ae;
    } catch (Exception e) {
      log.error("Error requesting CODEF access token", e);
      throw ApiException.internalServerError("CODEF 토큰 요청 실패", "CODEF_TOKEN_ERROR");
    }
  }

  private String getBaseUrl() {
    return sandboxBaseUrl;
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

}
