package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.dto.PolicyFeedDto;
import com.project.server.exception.ApiException;

import org.springframework.beans.factory.annotation.Value;
import com.project.server.repository.UserJpaRepository;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class PolicyFeedProxyService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final UserJpaRepository userJpaRepository;

    @Value("${integration.ml.base-url:http://localhost:9000}")
    private String mlBaseUrl;

    public PolicyFeedProxyService(ObjectMapper objectMapper, UserJpaRepository userJpaRepository) {
        this.objectMapper = objectMapper;
        this.userJpaRepository = userJpaRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    // Public methods for individual endpoints - each takes only required parameters

    public PolicyFeedDto.PolicyFeedResponse getPolicyFeed(Long userId, Integer limit, String category,
            String dateFrom, String dateTo) {
        PolicyFeedDto.PolicyFeedRequest request = PolicyFeedDto.PolicyFeedRequest.builder()
                .userId(userId)
                .limit(limit)
                .category(category)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .build();
        return fetchPolicyFeedWithValidation(request);
    }

    public java.util.Map<String, String> getMeta(Long userId) {
        PolicyFeedDto.PolicyFeedRequest request = PolicyFeedDto.PolicyFeedRequest.builder()
                .userId(userId)
                .build();
        PolicyFeedDto.PolicyFeedResponse response = fetchPolicyFeedWithValidation(request);
        return java.util.Map.of(
                "feedType", response.getFeedType() == null ? "" : response.getFeedType(),
                "generatedAt", response.getGeneratedAt() == null ? "" : response.getGeneratedAt());
    }

    public PolicyFeedDto.Source getSource(Long userId) {
        PolicyFeedDto.PolicyFeedRequest request = PolicyFeedDto.PolicyFeedRequest.builder()
                .userId(userId)
                .build();
        PolicyFeedDto.PolicyFeedResponse resp = fetchPolicyFeedWithValidation(request);
        if (resp.getSource() == null) {
            throw ApiException.notFound("피드 소스 정보 없음", "POLICY_FEED_SOURCE_NOT_FOUND");
        }
        return resp.getSource();
    }

    public PolicyFeedDto.Summary getSummary(Long userId) {
        PolicyFeedDto.PolicyFeedRequest request = PolicyFeedDto.PolicyFeedRequest.builder()
                .userId(userId)
                .build();
        PolicyFeedDto.PolicyFeedResponse resp = fetchPolicyFeedWithValidation(request);
        if (resp.getSummary() == null) {
            throw ApiException.notFound("피드 요약 정보 없음", "POLICY_FEED_SUMMARY_NOT_FOUND");
        }
        return resp.getSummary();
    }

    public PolicyFeedDto.Model getModel(Long userId) {
        PolicyFeedDto.PolicyFeedRequest request = PolicyFeedDto.PolicyFeedRequest.builder()
                .userId(userId)
                .build();
        PolicyFeedDto.PolicyFeedResponse resp = fetchPolicyFeedWithValidation(request);
        if (resp.getModel() == null) {
            throw ApiException.notFound("모델 정보 없음", "POLICY_FEED_MODEL_NOT_FOUND");
        }
        return resp.getModel();
    }

    public PolicyFeedDto.Filters getFilters(Long userId) {
        PolicyFeedDto.PolicyFeedRequest request = PolicyFeedDto.PolicyFeedRequest.builder()
                .userId(userId)
                .build();
        PolicyFeedDto.PolicyFeedResponse resp = fetchPolicyFeedWithValidation(request);
        if (resp.getFilters() == null) {
            throw ApiException.notFound("필터 정보 없음", "POLICY_FEED_FILTERS_NOT_FOUND");
        }
        return resp.getFilters();
    }

    public java.util.List<PolicyFeedDto.Card> getCards(Long userId, Integer limit, String category,
            String dateFrom, String dateTo) {
        PolicyFeedDto.PolicyFeedRequest request = PolicyFeedDto.PolicyFeedRequest.builder()
                .userId(userId)
                .limit(limit)
                .category(category)
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .build();
        PolicyFeedDto.PolicyFeedResponse resp = fetchPolicyFeedWithValidation(request);
        if (resp.getCards() == null || resp.getCards().isEmpty()) {
            throw ApiException.notFound("카드 목록 없음", "POLICY_FEED_CARDS_NOT_FOUND");
        }
        return resp.getCards();
    }

    // Internal validation and fetch logic
    private PolicyFeedDto.PolicyFeedResponse fetchPolicyFeedWithValidation(
            PolicyFeedDto.PolicyFeedRequest requestBody) {
        PolicyFeedDto.PolicyFeedRequest safeRequest = requestBody == null
                ? PolicyFeedDto.PolicyFeedRequest.builder().build()
                : requestBody;

        // Validate inputs
        int limit = safeRequest.getLimit() == null ? 20 : safeRequest.getLimit();
        if (limit <= 0 || limit > 200) {
            throw ApiException.badRequest("limit은 1~200 범위여야 합니다.", "POLICY_FEED_INVALID_LIMIT");
        }
        Long userIdObj = safeRequest.getUserId();
        if (userIdObj == null) {
            throw ApiException.badRequest("userId는 필수 파라미터입니다.", "POLICY_FEED_MISSING_USERID");
        }
        long userId = userIdObj;
        if (userId <= 0) {
            throw ApiException.badRequest("userId는 양수여야 합니다.", "POLICY_FEED_INVALID_USERID");
        }

        // Check user existence
        boolean userExists = userJpaRepository.existsById(userId);
        if (!userExists) {
            throw ApiException.notFound("존재하지 않는 사용자입니다.", "POLICY_FEED_USER_NOT_FOUND");
        }

        PolicyFeedDto.PolicyFeedResponse mlResp;
        try {
            mlResp = fetchPolicyFeedFromMl(safeRequest, "live-request");
        } catch (ApiException ae) {
            // All ApiException from ML (4xx, 5xx) -> propagate immediately
            throw ae;
        } catch (Exception ex) {
            // Unexpected errors (network timeout, serialization, etc.) -> treat as server
            // error
            throw ApiException.internalServerError("정책 피드 데이터 조회 실패", "POLICY_FEED_FETCH_ERROR");
        }

        // Validate fetched data
        if (mlResp == null || mlResp.getCards() == null || mlResp.getCards().isEmpty()) {
            throw ApiException.internalServerError("정책 피드 데이터가 비어있습니다.", "POLICY_FEED_NO_DATA");
        }

        // Build filters dynamically if null
        if (mlResp.getFilters() == null) {
            mlResp.setFilters(buildFiltersFromCards(mlResp.getCards()));
        }

        return mlResp;
    }

    private PolicyFeedDto.Filters buildFiltersFromCards(java.util.List<PolicyFeedDto.Card> cards) {
        Set<String> categories = new LinkedHashSet<>();
        Set<String> docTypes = new LinkedHashSet<>();
        double minSentiment = Double.MAX_VALUE;
        double maxSentiment = -Double.MAX_VALUE;
        LocalDate minDate = null;
        LocalDate maxDate = null;

        for (PolicyFeedDto.Card card : cards) {
            // Extract categories
            if (card.getCategory() != null && !card.getCategory().isBlank()) {
                categories.add(card.getCategory());
            }

            // Extract docTypes
            if (card.getDocType() != null && !card.getDocType().isBlank()) {
                docTypes.add(card.getDocType());
            }

            // Calculate sentiment range
            if (card.getSentiment() != null && card.getSentiment().getTitleSentimentScore() != null) {
                double sentimentScore = card.getSentiment().getTitleSentimentScore();
                minSentiment = Math.min(minSentiment, sentimentScore);
                maxSentiment = Math.max(maxSentiment, sentimentScore);
            }

            // Calculate date range
            if (card.getDate() != null && !card.getDate().isBlank()) {
                try {
                    LocalDate cardDate = LocalDate.parse(card.getDate());
                    if (minDate == null || cardDate.isBefore(minDate)) {
                        minDate = cardDate;
                    }
                    if (maxDate == null || cardDate.isAfter(maxDate)) {
                        maxDate = cardDate;
                    }
                } catch (Exception e) {
                    // Date parsing failed, skip
                }
            }
        }

        return PolicyFeedDto.Filters.builder()
                .categories(new ArrayList<>(categories))
                .docTypes(new ArrayList<>(docTypes))
                .dateRange(PolicyFeedDto.DateRange.builder()
                        .from(minDate != null ? minDate.toString() : "")
                        .to(maxDate != null ? maxDate.toString() : "")
                        .build())
                .sentimentRange(PolicyFeedDto.SentimentRange.builder()
                        .min(minSentiment == Double.MAX_VALUE ? 0.0 : minSentiment)
                        .max(maxSentiment == -Double.MAX_VALUE ? 0.0 : maxSentiment)
                        .build())
                .build();
    }

    private PolicyFeedDto.PolicyFeedResponse fetchPolicyFeedFromMl(PolicyFeedDto.PolicyFeedRequest request,
            String reason) {
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

            HttpResponse<String> response = httpClient.send(httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode resultNode = root.has("result") ? root.get("result") : root;
            if (resultNode == null || resultNode.isNull()) {
                return null;
            }
            return objectMapper.treeToValue(resultNode, PolicyFeedDto.PolicyFeedResponse.class);
        } catch (ApiException ae) {
            throw ae;
        } catch (Exception exception) {
            return null;
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
}