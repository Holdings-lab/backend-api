package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.server.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class MlSignalProxyService {

    private static final Logger log = LoggerFactory.getLogger(MlSignalProxyService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(130);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${integration.ml.base-url:http://localhost:9000}")
    private String mlBaseUrl;

    public MlSignalProxyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public JsonNode runSignal(String ticker) {
        String targetUrl = normalizeBaseUrl(mlBaseUrl) + "/ml/signal/run";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("ticker", ticker == null || ticker.isBlank() ? "QQQ" : ticker.trim().toUpperCase());

        log.info("Calling ML signal endpoint: url={}, ticker={}", targetUrl, body.path("ticker").asText());

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SIGNAL-500",
                    "시그널 요청 생성에 실패했습니다. url=" + targetUrl + ", cause=" + rootMessage(exception));
        }

        try {
            CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            HttpResponse<String> response = future.join();

            if (response.statusCode() == 409) {
                throw new ApiException(HttpStatus.CONFLICT, "SIGNAL-BUSY", "ML 서비스가 현재 다른 작업을 수행 중입니다.");
            }
            if (response.statusCode() == 504) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "SIGNAL-TIMEOUT", "시그널 예측이 시간 초과되었습니다.");
            }
            if (response.statusCode() == 404) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "SIGNAL-FEATURES-OR-RESULT",
                        upstreamMessage(response.body(), "시그널 feature 또는 결과 파일을 찾을 수 없습니다.")
                                + " url=" + targetUrl);
            }
            if (response.statusCode() >= 500) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "SIGNAL-UPSTREAM-500",
                        upstreamMessage(response.body(), "ML 서비스 시그널 호출에 실패했습니다.")
                                + " url=" + targetUrl);
            }
            if (response.statusCode() >= 400) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "SIGNAL-UPSTREAM-400",
                        upstreamMessage(response.body(), "ML 서비스 시그널 요청이 거부되었습니다.")
                                + " url=" + targetUrl);
            }

            JsonNode responseBody = objectMapper.readTree(response.body());
            JsonNode result = responseBody.path("result");
            if (result.isMissingNode() || result.isNull()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "SIGNAL-EMPTY", "시그널 예측 결과가 비어 있습니다.");
            }
            return result;
        } catch (CompletionException completionException) {
            String cause = rootMessage(completionException);
            log.warn("ML signal upstream connect failed: url={}, cause={}", targetUrl, cause);
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "SIGNAL-UPSTREAM-CONNECT",
                    "ML 서비스에 연결할 수 없습니다. url=" + targetUrl + ", cause=" + cause);
        } catch (ApiException apiException) {
            throw apiException;
        } catch (Exception exception) {
            log.warn("ML signal processing failed: url={}, cause={}", targetUrl, rootMessage(exception));
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "SIGNAL-500",
                    "시그널 예측 처리 중 오류가 발생했습니다. url=" + targetUrl + ", cause=" + rootMessage(exception));
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:9000";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String upstreamMessage(String responseBody, String fallback) {
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            String message = body.path("message").asText("");
            if (!message.isBlank()) {
                return message;
            }
        } catch (Exception ignored) {
            // fallback
        }
        return fallback;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.isBlank()) {
            return current.getClass().getSimpleName();
        }
        return current.getClass().getSimpleName() + ": " + message;
    }
}
