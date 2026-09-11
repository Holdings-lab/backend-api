package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NewsroomBriefingProxyService {

    private static final Logger logger = LoggerFactory.getLogger(NewsroomBriefingProxyService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${integration.ml.base-url:http://localhost:9000}")
    private String mlBaseUrl;

    public NewsroomBriefingProxyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public Map<String, SectorBriefing> getSectorBriefings(Collection<String> sectors, LocalDate briefingDate) {
        if (sectors == null || sectors.isEmpty()) {
            return Collections.emptyMap();
        }
        String sectorParam = sectors.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .collect(Collectors.joining(","));
        if (sectorParam.isBlank()) {
            return Collections.emptyMap();
        }

        try {
            StringBuilder url = new StringBuilder(mlBaseUrl.replaceAll("/$", ""));
            url.append("/ml/newsroom/sector-briefings?sectors=")
                    .append(URLEncoder.encode(sectorParam, StandardCharsets.UTF_8));
            if (briefingDate != null) {
                url.append("&briefingDate=")
                        .append(URLEncoder.encode(briefingDate.toString(), StandardCharsets.UTF_8));
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                logger.warn("Newsroom sector briefing ML error status={}, body={}",
                        response.statusCode(), truncate(response.body()));
                return Collections.emptyMap();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.has("result") ? root.get("result") : root;
            JsonNode sectorsNode = result == null ? null : result.get("sectors");
            if (sectorsNode == null || !sectorsNode.isObject()) {
                return Collections.emptyMap();
            }

            Map<String, SectorBriefing> mapped = new LinkedHashMap<>();
            sectorsNode.fields().forEachRemaining(entry -> {
                String sector = entry.getKey() == null ? null : entry.getKey().toLowerCase(Locale.ROOT);
                JsonNode value = entry.getValue();
                if (sector == null || value == null || value.isNull()) {
                    return;
                }
                DailySummary daily = readDaily(value.get("dailySummary"));
                AiBriefing ai = readAi(value.get("aiBriefing"));
                mapped.put(sector, new SectorBriefing(daily, ai));
            });
            return mapped;
        } catch (Exception ex) {
            logger.warn("Newsroom sector briefing ML request failed: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }

    private DailySummary readDaily(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return new DailySummary(
                text(node.get("title")),
                text(node.get("content")),
                sanitizeUrl(text(node.get("imageUrl")))
        );
    }

    private AiBriefing readAi(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return new AiBriefing(
                text(node.get("title")),
                text(node.get("headline")),
                text(node.get("reason"))
        );
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "nan".equalsIgnoreCase(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String sanitizeUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "nan".equalsIgnoreCase(trimmed) || "null".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 300 ? body : body.substring(0, 300) + "...";
    }

    public record SectorBriefing(DailySummary dailySummary, AiBriefing aiBriefing) {
    }

    public record DailySummary(String title, String content, String imageUrl) {
    }

    public record AiBriefing(String title, String headline, String reason) {
    }
}
