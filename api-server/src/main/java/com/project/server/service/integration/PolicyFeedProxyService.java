package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.dto.PolicyFeedDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class PolicyFeedProxyService {

    private static final Logger log = LoggerFactory.getLogger(PolicyFeedProxyService.class);

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final HttpClient httpClient;

    @Value("${integration.ml.base-url:http://localhost:9000}")
    private String mlBaseUrl;

    public PolicyFeedProxyService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public PolicyFeedDto.PolicyFeedResponse getPolicyFeed(PolicyFeedDto.PolicyFeedRequest requestBody) {
        PolicyFeedDto.PolicyFeedRequest safeRequest = requestBody == null
                ? PolicyFeedDto.PolicyFeedRequest.builder().build()
                : requestBody;
        int limit = safeRequest.getLimit() == null ? 20 : safeRequest.getLimit();
        String category = isBlank(safeRequest.getCategory()) ? "all" : safeRequest.getCategory().trim();
        String dateFrom = isBlank(safeRequest.getDateFrom()) ? "" : safeRequest.getDateFrom().trim();
        String dateTo = isBlank(safeRequest.getDateTo()) ? "" : safeRequest.getDateTo().trim();
        long userId = safeRequest.getUserId() == null ? 1L : safeRequest.getUserId();

        try {
            String json = jdbcTemplate.query(
                    """
                    SELECT result_json::text
                      FROM ml_policy_feed_snapshot
                     WHERE feed_limit = ?
                       AND category = ?
                       AND date_from = ?
                       AND date_to = ?
                       AND user_id = ?
                     ORDER BY updated_at DESC
                     LIMIT 1
                    """,
                    rs -> rs.next() ? rs.getString(1) : null,
                    limit,
                    category,
                    dateFrom,
                    dateTo,
                    userId
            );

            if (json != null) {
                JsonNode resultNode = objectMapper.readTree(json);
                return objectMapper.treeToValue(resultNode, PolicyFeedDto.PolicyFeedResponse.class);
            }

            return fetchPolicyFeedFromMl(safeRequest, "db-miss");
        } catch (Exception exception) {
            log.warn("policy-feed DB read failed, trying ML fallback", exception);
            return fetchPolicyFeedFromMl(safeRequest, "db-error");
        }
    }

    private PolicyFeedDto.PolicyFeedResponse fetchPolicyFeedFromMl(PolicyFeedDto.PolicyFeedRequest request, String reason) {
        try {
            String targetUrl = normalizeBaseUrl(mlBaseUrl) + "/ml/content/policy-feed";
            String requestJson = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("policy-feed ML fallback failed: status={}, reason={}", response.statusCode(), reason);
                return emptyResponse();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode resultNode = root.has("result") ? root.get("result") : root;
            if (resultNode == null || resultNode.isNull()) {
                return emptyResponse();
            }
            return objectMapper.treeToValue(resultNode, PolicyFeedDto.PolicyFeedResponse.class);
        } catch (Exception exception) {
            log.warn("policy-feed ML fallback error: reason={}", reason, exception);
            return emptyResponse();
        }
    }

    private PolicyFeedDto.PolicyFeedResponse emptyResponse() {
        return PolicyFeedDto.PolicyFeedResponse.builder()
                .feedType("policy_news_with_model_signal")
                .generatedAt(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .cards(java.util.List.of())
                .build();
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}