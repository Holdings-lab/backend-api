package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class MlPredictionProxyService {

    @Value("${integration.ml.base-url:http://localhost:9000}")
    private String mlBaseUrl;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MlPredictionProxyService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public JsonNode fetchPredictionResult() {
        try {
            String url = mlBaseUrl.endsWith("/") ? mlBaseUrl.substring(0, mlBaseUrl.length() - 1) : mlBaseUrl;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/ml/predict/result"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return null;
            }
            return objectMapper.readTree(response.body()).path("result");
        } catch (Exception e) {
            return null;
        }
    }
}
