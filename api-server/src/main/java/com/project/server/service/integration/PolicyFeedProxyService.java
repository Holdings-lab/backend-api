package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.dto.PolicyFeedDto;
import com.project.server.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class PolicyFeedProxyService {

    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public PolicyFeedProxyService(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
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

            if (json == null) {
                return PolicyFeedDto.PolicyFeedResponse.builder()
                        .feedType("policy_news_with_model_signal")
                        .generatedAt(OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .cards(java.util.List.of())
                        .build();
            }

            JsonNode resultNode = objectMapper.readTree(json);
            return objectMapper.treeToValue(resultNode, PolicyFeedDto.PolicyFeedResponse.class);
        } catch (ApiException apiException) {
            throw apiException;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FEED-500", "정책 피드 조회 처리 중 오류가 발생했습니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}