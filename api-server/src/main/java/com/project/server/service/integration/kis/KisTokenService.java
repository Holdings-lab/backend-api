package com.project.server.service.integration.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.config.KisProperties;
import com.project.server.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class KisTokenService {

    private static final String TOKEN_PATH = "/oauth2/tokenP";

    private final ObjectMapper objectMapper;
    private final KisProperties kisProperties;
    private final Object tokenLock = new Object();
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public String getAccessToken(String appKey, String appSecret) {
        CachedToken cached = tokens.get(appKey);
        Instant now = Instant.now();
        if (cached != null && now.isBefore(cached.expiresAt().minusSeconds(60))) {
            return cached.accessToken();
        }

        synchronized (tokenLock) {
            cached = tokens.get(appKey);
            now = Instant.now();
            if (cached != null && now.isBefore(cached.expiresAt().minusSeconds(60))) {
                return cached.accessToken();
            }
            return refreshToken(appKey, appSecret);
        }
    }

    public void invalidate(String appKey) {
        if (appKey != null) {
            tokens.remove(appKey);
        }
    }

    private String refreshToken(String appKey, String appSecret) {
        try {
            Map<String, String> body = Map.of(
                    "grant_type", "client_credentials",
                    "appkey", appKey,
                    "appsecret", appSecret);
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + TOKEN_PATH))
                    .timeout(Duration.ofSeconds(kisProperties.getApi().getTimeoutSeconds()))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("KIS token request failed: {} - {}", response.statusCode(), response.body());
                throw ApiException.internalServerError("KIS 통신 오류가 발생했습니다.", "KIS_OAUTH_ERROR");
            }

            JsonNode tokenResponse = objectMapper.readTree(response.body());
            String accessToken = tokenResponse.path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw ApiException.internalServerError("KIS 통신 오류가 발생했습니다.", "KIS_OAUTH_ERROR");
            }

            long expiresIn = tokenResponse.path("expires_in").asLong(86400L);
            tokens.put(appKey, new CachedToken(accessToken, Instant.now().plusSeconds(expiresIn)));
            log.info("KIS OAuth token refreshed, expires in {} seconds", expiresIn);
            return accessToken;
        } catch (ApiException ae) {
            throw ae;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ApiException.internalServerError("KIS 통신 오류가 발생했습니다.", "KIS_OAUTH_INTERRUPTED");
        } catch (Exception e) {
            log.error("Failed to obtain KIS OAuth token", e);
            throw ApiException.internalServerError("KIS 통신 오류가 발생했습니다.", "KIS_OAUTH_ERROR");
        }
    }

    private String baseUrl() {
        if (kisProperties.isPaperMode()) {
            return kisProperties.getApi().getPaperBaseUrl();
        }
        return kisProperties.getApi().getRealBaseUrl();
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
