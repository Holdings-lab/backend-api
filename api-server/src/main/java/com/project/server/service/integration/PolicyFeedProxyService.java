package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.dto.PolicyFeedDto;
import com.project.server.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletionException;

@Service
public class PolicyFeedProxyService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${integration.ml.base-url:http://localhost:9000}")
    private String mlBaseUrl;

    public PolicyFeedProxyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public PolicyFeedDto.PolicyFeedResponse getPolicyFeed(PolicyFeedDto.PolicyFeedRequest requestBody) {
        String targetUrl = normalizeBaseUrl(mlBaseUrl) + "/ml/content/policy-feed";

        PolicyFeedDto.PolicyFeedRequest safeRequest = requestBody == null
                ? PolicyFeedDto.PolicyFeedRequest.builder().build()
                : requestBody;

        try {
            String payload = objectMapper.writeValueAsString(safeRequest);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).join();

            if (response.statusCode() == 409) {
                throw new ApiException(HttpStatus.CONFLICT, "FEED-BUSY", "ML 서비스가 현재 다른 작업을 수행 중입니다.");
            }
            if (response.statusCode() >= 500) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "FEED-UPSTREAM-500", "ML 서비스 호출에 실패했습니다.");
            }
            if (response.statusCode() >= 400) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FEED-UPSTREAM-400", "ML 서비스 요청이 거부되었습니다.");
            }

            JsonNode envelope = objectMapper.readTree(response.body());
            JsonNode resultNode = envelope.path("result");
            return objectMapper.treeToValue(resultNode, PolicyFeedDto.PolicyFeedResponse.class);
        } catch (CompletionException completionException) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FEED-UPSTREAM-CONNECT", "ML 서비스에 연결할 수 없습니다.");
        } catch (ApiException apiException) {
            throw apiException;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FEED-500", "정책 피드 조회 처리 중 오류가 발생했습니다.");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}