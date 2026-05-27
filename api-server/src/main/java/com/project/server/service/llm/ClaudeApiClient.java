package com.project.server.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "anthropic")
public class ClaudeApiClient implements LlmApiService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String modelName;

    public ClaudeApiClient(
            ObjectMapper objectMapper,
            @Value("${ANTHROPIC_API_KEY:}") String apiKey,
            @Value("${ANTHROPIC_MODEL:claude-3.5-haiku-20241022}") String modelName
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String getProviderName() {
        return "anthropic";
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public Map<String, Object> generateJson(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is required");
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", modelName,
                    "max_tokens", 1024,
                    "temperature", 0.2,
                    "system", systemPrompt,
                    "messages", List.of(Map.of("role", "user", "content", userPrompt))
            );
            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.anthropic.com/v1/messages"))
                    .timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Anthropic API error: " + response.body());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("content");
            StringBuilder builder = new StringBuilder();
            if (content.isArray()) {
                for (JsonNode part : content) {
                    builder.append(part.path("text").asText(""));
                }
            }
            JsonNode parsed = objectMapper.readTree(stripCodeFence(builder.toString()));
            return objectMapper.convertValue(parsed, Map.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Anthropic JSON generation failed", exception);
        }
    }

    private String stripCodeFence(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
            normalized = normalized.replaceFirst("\\s*```$", "");
        }
        return normalized.trim();
    }
}
