package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hyphen.api.mode", havingValue = "live", matchIfMissing = true)
public class HyphenApiClientService implements HyphenApiClient {

    private static final String OAUTH_TOKEN_ENDPOINT = "/oauth/token";

    /** in0104000534 - 개인계좌 증권사 전계좌조회 */
    private static final String ALL_ACCOUNTS_ENDPOINT = "/in0104000534";
    /** in0104000535 - 개인계좌 증권사 거래내역조회 (입출금 등) */
    private static final String DEPOSIT_WITHDRAW_HISTORY_ENDPOINT = "/in0104000535";
    /** in0104000536 - 개인계좌 증권사 잔액조회 (입출금/외화/대출 잔액) */
    private static final String CASH_BALANCE_ENDPOINT = "/in0104000536";
    /** in0104000539 - 개인계좌 증권사 잔고조회 (보유종목·평가) */
    private static final String HOLDINGS_ENDPOINT = "/in0104000539";
    /** in0104000540 - 개인계좌 증권사 자산거래내역조회 (매수/매도 등) */
    private static final String ASSET_TRANSACTION_HISTORY_ENDPOINT = "/in0104000540";

    private final ObjectMapper objectMapper;
    private final Object tokenLock = new Object();

    @Value("${hyphen.api.base-url:https://api.hyphen.im}")
    private String baseUrl;

    @Value("${hyphen.user-id:}")
    private String hyphenUserId;

    @Value("${hyphen.hkey:}")
    private String hyphenHkey;

    @Value("${hyphen.oauth.enabled:true}")
    private boolean oauthEnabled;

    @Value("${hyphen.api.timeout-seconds:12}")
    private long apiTimeoutSeconds;

    @Value("${hyphen.api.max-retries:3}")
    private int maxRetries;

    @Value("${hyphen.api.gustation-enabled:false}")
    private boolean gustationEnabled;

    private volatile String cachedAccessToken;
    private volatile Instant tokenExpiresAt;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** 전계좌조회 - 전계좌 목록 (in0104000534) */
    @Override
    public JsonNode fetchAccountList(HyphenCredential credential, String brokerName) {
        Map<String, Object> body = buildAuthBody(credential, brokerName, null, true);
        return callHyphenApi(ALL_ACCOUNTS_ENDPOINT, body);
    }

    /** 잔액조회 - 입출금/외화/대출 잔액 (in0104000536) */
    @Override
    public JsonNode fetchCashBalance(HyphenCredential credential, String brokerName, String accountNumber) {
        Map<String, Object> body = buildAuthBody(credential, brokerName, accountNumber, false);
        return callHyphenApi(CASH_BALANCE_ENDPOINT, body);
    }

    /** 잔고조회 - 보유종목·평가금액 (in0104000539) */
    @Override
    public JsonNode fetchHoldings(HyphenCredential credential, String brokerName, String accountNumber) {
        Map<String, Object> body = buildAuthBody(credential, brokerName, accountNumber, true);
        return callHyphenApi(HOLDINGS_ENDPOINT, body);
    }

    /** 거래내역조회 - 입출금·적요 (in0104000535) */
    @Override
    public JsonNode fetchDepositWithdrawHistory(
            HyphenCredential credential,
            String brokerName,
            String accountNumber,
            String fromDate,
            String toDate) {
        Map<String, Object> body = buildAuthBody(credential, brokerName, accountNumber, false);
        body.put("sdate", fromDate);
        body.put("edate", toDate);
        body.put("sort", "NEW");
        body.put("productCd", "");
        body.put("divCheckOpt", "00");
        body.put("ctrNo", "");
        body.put("detailYn", "N");
        return callHyphenApi(DEPOSIT_WITHDRAW_HISTORY_ENDPOINT, body);
    }

    /** 자산거래내역조회 - 매수/매도 등 (in0104000540) */
    @Override
    public JsonNode fetchAssetTransactionHistory(
            HyphenCredential credential,
            String brokerName,
            String accountNumber,
            String fromDate,
            String toDate) {
        Map<String, Object> body = buildAuthBody(credential, brokerName, accountNumber, true);
        body.put("productCd", "");
        body.put("sdate", fromDate);
        body.put("edate", toDate);
        body.put("allCheckYn", "Y");
        body.put("divCheckOpt", "00");
        body.put("divCheckOpt2", "0");
        body.put("incRPOpt", "N");
        body.put("incMMWOpt", "N");
        return callHyphenApi(ASSET_TRANSACTION_HISTORY_ENDPOINT, body);
    }

    private Map<String, Object> buildAuthBody(
            HyphenCredential credential,
            String brokerName,
            String accountNumber,
            boolean includeLoginRequired) {
        if (credential == null || isBlank(credential.userId()) || isBlank(credential.userPw())) {
            throw ApiException.badRequest("하이픈 연동 사용자 정보가 누락되었습니다.", "HYPHEN_LOGIN_INFO_MISSING");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("bankCd", getBankCode(brokerName));
        body.put("loginMethod", defaultString(credential.loginMethod(), "ID"));
        if (includeLoginRequired) {
            body.put("loginRequired", defaultString(credential.loginRequired(), "N"));
        }
        body.put("userId", credential.userId());
        body.put("userPw", credential.userPw());
        body.put("signCert", ""); // 인증서로그인 - 공인인증서모듈설치 필요
        body.put("signPri", "");
        body.put("signPw", "");
        body.put("acctNo", defaultString(accountNumber, ""));
        body.put("acctPw", defaultString(credential.accountPassword(), ""));
        return body;
    }

    private JsonNode callHyphenApi(String endpoint, Map<String, Object> body) {
        ensureHyphenHeadersConfigured();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String requestBody = objectMapper.writeValueAsString(body);
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + endpoint))
                        .timeout(Duration.ofSeconds(apiTimeoutSeconds))
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("user-tr-no", UUID.randomUUID().toString())
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

                applyAuthHeaders(builder);

                if (gustationEnabled) {
                    builder.header("Hyphen-Gustation", "Y");
                }

                HttpResponse<String> response = httpClient.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() == 401 && oauthEnabled && attempt < maxRetries) {
                    invalidateAccessToken();
                    log.warn("Hyphen token expired on attempt {}, refreshing...", attempt);
                    continue;
                }

                if (response.statusCode() >= 500 && attempt < maxRetries) {
                    log.warn("Hyphen server error on attempt {}, retrying...", attempt);
                    Thread.sleep(1000L * attempt);
                    continue;
                }

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.error("Hyphen API error: {} - {}", response.statusCode(), response.body());
                    throw ApiException.internalServerError("하이픈 API 호출 실패", "HYPHEN_API_ERROR");
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode common = root.path("common");
                if ("Y".equalsIgnoreCase(common.path("errYn").asText("N"))) {
                    String errCode = common.path("errCd").asText("HYPHEN_ERROR");
                    String errMsg = common.path("errMsg").asText("하이픈 API 호출 실패");
                    throw ApiException.badRequest(errMsg, errCode);
                }

                return root;

            } catch (ApiException ae) {
                throw ae;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw ApiException.internalServerError("API 요청 중단됨", "HYPHEN_INTERRUPTED");
            } catch (Exception e) {
                log.error("Error calling Hyphen API endpoint={}", endpoint, e);
                if (attempt == maxRetries) {
                    throw ApiException.internalServerError("하이픈 API 호출 실패", "HYPHEN_API_ERROR");
                }
            }
        }

        throw ApiException.internalServerError("하이픈 API 호출 실패", "HYPHEN_API_ERROR");
    }

    private void applyAuthHeaders(HttpRequest.Builder builder) {
        if (oauthEnabled) {
            builder.header("Authorization", "Bearer " + getOrRefreshAccessToken());
            return;
        }
        builder.header("user-id", hyphenUserId);
        builder.header("hkey", hyphenHkey);
    }

    private String getOrRefreshAccessToken() {
        Instant now = Instant.now();
        if (cachedAccessToken != null && tokenExpiresAt != null && now.isBefore(tokenExpiresAt.minusSeconds(60))) {
            return cachedAccessToken;
        }

        synchronized (tokenLock) {
            now = Instant.now();
            if (cachedAccessToken != null && tokenExpiresAt != null && now.isBefore(tokenExpiresAt.minusSeconds(60))) {
                return cachedAccessToken;
            }

            try {
                Map<String, String> tokenRequest = Map.of(
                        "user_id", hyphenUserId,
                        "hkey", hyphenHkey);

                String requestBody = objectMapper.writeValueAsString(tokenRequest);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + OAUTH_TOKEN_ENDPOINT))
                        .timeout(Duration.ofSeconds(apiTimeoutSeconds))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.error("Hyphen OAuth token request failed: {} - {}", response.statusCode(), response.body());
                    throw ApiException.internalServerError("하이픈 OAuth 토큰 발급 실패", "HYPHEN_OAUTH_ERROR");
                }

                JsonNode tokenResponse = objectMapper.readTree(response.body());
                String accessToken = tokenResponse.path("access_token").asText(null);
                if (isBlank(accessToken)) {
                    throw ApiException.internalServerError("하이픈 OAuth 토큰 응답이 올바르지 않습니다.", "HYPHEN_OAUTH_ERROR");
                }

                long expiresIn = tokenResponse.path("expires_in").asLong(604800L);
                cachedAccessToken = accessToken;
                tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
                log.info("Hyphen OAuth token refreshed, expires in {} seconds", expiresIn);
                return cachedAccessToken;

            } catch (ApiException ae) {
                throw ae;
            } catch (Exception e) {
                log.error("Failed to obtain Hyphen OAuth token", e);
                throw ApiException.internalServerError("하이픈 OAuth 토큰 발급 실패", "HYPHEN_OAUTH_ERROR");
            }
        }
    }

    private void invalidateAccessToken() {
        synchronized (tokenLock) {
            cachedAccessToken = null;
            tokenExpiresAt = null;
        }
    }

    private String getBankCode(String brokerName) {
        if (brokerName == null || brokerName.isBlank()) {
            return "0243";
        }
        String trimmed = brokerName.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            return trimmed;
        }
        return BROKER_BANK_CODES.getOrDefault(trimmed.toUpperCase(), "0243");
    }

    private void ensureHyphenHeadersConfigured() {
        if (isBlank(hyphenUserId) || isBlank(hyphenHkey)) {
            throw ApiException.internalServerError(
                    "하이픈 인증 정보가 설정되지 않았습니다. HYPHEN_USER_ID/HYPHEN_HKEY를 확인하세요.",
                    "HYPHEN_CONFIG_MISSING");
        }
    }

    private static String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
