package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class MlHealthProxyService {

    private static final Logger log = LoggerFactory.getLogger(MlHealthProxyService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${integration.ml.base-url:http://localhost:9000}")
    private String mlBaseUrl;

    public MlHealthProxyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public Map<String, Object> fetch() {
        String base = mlBaseUrl == null ? "" : mlBaseUrl.replaceAll("/$", "");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/ml/health"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return Map.of("status", "unavailable");
            }
            JsonNode result = objectMapper.readTree(response.body()).path("result");
            if (result.isMissingNode() || result.isNull()) {
                return Map.of("status", "unavailable");
            }
            return objectMapper.convertValue(result, objectMapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, Object.class));
        } catch (Exception exception) {
            log.warn("ML health proxy failed: {}", exception.getMessage());
            return Map.of("status", "unavailable");
        }
    }
}
