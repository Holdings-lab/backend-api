package com.project.server.service.integration;

import com.project.server.dto.ActionDto;
import com.project.server.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class RegressionTrainingService {

    private static final int OUTPUT_TAIL_LIMIT = 5000;
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(11);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${integration.ml.base-url:http://localhost:9000}")
    private String mlBaseUrl;

    public RegressionTrainingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public ActionDto.TrainRegressionResponse runTrainRegression() {
        String targetUrl = normalizeBaseUrl(mlBaseUrl) + "/ml/predict/run";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        Instant startedAt = Instant.now();
        try {
            CompletableFuture<HttpResponse<String>> future = httpClient.sendAsync(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            HttpResponse<String> response = future.join();
            long elapsedMs = Duration.between(startedAt, Instant.now()).toMillis();

            if (response.statusCode() == 409) {
                throw new ApiException(HttpStatus.CONFLICT, "TRAIN-BUSY", "ML 서비스가 현재 다른 작업을 수행 중입니다.");
            }

            if (response.statusCode() >= 500) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "TRAIN-UPSTREAM-500", "ML 서비스 호출에 실패했습니다.");
            }

            if (response.statusCode() >= 400) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TRAIN-UPSTREAM-400", "ML 서비스 요청이 거부되었습니다.");
            }

            JsonNode body = objectMapper.readTree(response.body());
            JsonNode result = body.path("result");
            int exitCode = result.path("return_code").asInt(-1);
            String stdoutTail = result.path("stdout_tail").asText("");
            String stderrTail = result.path("stderr_tail").asText("");

            return ActionDto.TrainRegressionResponse.builder()
                    .scriptPath("data-ml/training/train_regression.py")
                    .command("POST " + targetUrl)
                    .exitCode(exitCode)
                    .elapsedMs(elapsedMs)
                    .stdoutTail(tail(stdoutTail, OUTPUT_TAIL_LIMIT))
                    .stderrTail(tail(stderrTail, OUTPUT_TAIL_LIMIT))
                    .fallbackPaths(List.of(targetUrl))
                    .build();
        } catch (CompletionException completionException) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TRAIN-UPSTREAM-CONNECT", "ML 서비스에 연결할 수 없습니다.");
        } catch (ApiException apiException) {
            throw apiException;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TRAIN-500", "학습 실행 처리 중 오류가 발생했습니다.");
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String tail(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(value.length() - maxLength);
    }
}
