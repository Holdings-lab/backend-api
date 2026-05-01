package com.project.server.service.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.exception.ApiException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class SignalDetailService {

        @Value("${integration.ml.base-url:http://localhost:9000}")
        private String mlBaseUrl;

        private final ObjectMapper objectMapper;
        private final HttpClient httpClient;

        public SignalDetailService(ObjectMapper objectMapper) {
                this.objectMapper = objectMapper;
                this.httpClient = HttpClient.newBuilder()
                                .connectTimeout(Duration.ofSeconds(5))
                                .build();
        }

        private JsonNode fetchMlResult() {
                try {
                        String url = mlBaseUrl.endsWith("/") ? mlBaseUrl.substring(0, mlBaseUrl.length() - 1)
                                        : mlBaseUrl;
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

        public Map<String, Object> getDynamicSignalDetail(String id, List<String> mockAssets) {
                JsonNode result = fetchMlResult();
                if (result == null || result.isMissingNode()) {
                        throw ApiException.notFound("ML 예측 결과를 불러올 수 없습니다.", "ML_DATA_NOT_FOUND");
                }

                JsonNode metrics = result.path("metrics");
                double policyScore = metrics.path("policyScore").asDouble(0.0);
                double threshold = result.path("bestThreshold").asDouble(0.004);
                double accuracy = metrics.path("directionAccuracy").asDouble(0.5);
                double topProb = metrics.path("topLabelProbability").asDouble(0.0);
                String topLabel = metrics.path("topLabel").asText("flat");
                String generatedAt = result.path("generatedAt").asText("");

                JsonNode topFeatures = result.path("topFeatureImportance");
                String topFeature = topFeatures.isArray() && topFeatures.size() > 0
                                ? topFeatures.get(0).path("feature").asText()
                                : "알 수 없음";

                Map<String, Object> decisionBadge;
                if (policyScore > threshold) {
                        decisionBadge = Map.of("label", "매수 포지션 확대", "color", "GREEN", "icon", "UP");
                } else if (policyScore < -threshold) {
                        decisionBadge = Map.of("label", "비중 축소 필요", "color", "RED", "icon", "DOWN");
                } else {
                        decisionBadge = Map.of("label", "방어 비중 점검", "color", "ORANGE", "icon", "SHIELD");
                }

                Map<String, Object> impactZone = Map.of(
                                "exposurePercent", topProb * 100,
                                "description",
                                "보유하신 " + String.join(", ", mockAssets) + " 자산군의 단기 방향성에 직접적인 영향을 줄 확률입니다.");

                String keyReason = "핵심 요인(" + topFeature + ") 변화로 인한 시장 센티먼트 이동";
                Map<String, Object> keyNumbers = Map.of("keyFigure", String.format("%.2f%%", policyScore * 100),
                                "context",
                                "단기 기대 변화율(예측치)");
                String revisitTime = "예측 업데이트 일시: " + generatedAt;
                String weakeningCondition = "단, 변동성(Volatility) 장세일 경우 예측의 신뢰도가 낮아질 수 있습니다.";
                String behaviorTips = String.format("방향성 적중 확률(%.1f%%) 기반 전략적 리밸런싱을 권장합니다.", accuracy * 100);

                List<Map<String, Object>> impactPath = Collections
                                .singletonList(Map.of("step", "모델 특징", "desc", "요인: " + topFeature, "icon", "BANK"));
                List<String> refinedEvidence = Collections.singletonList(
                                "과거 데이터 기반 가장 유력한 시나리오: " + topLabel + " (확률 " + String.format("%.1f%%", topProb * 100)
                                                + ")");
                Map<String, Object> counterArguments = Map.of("title", "반대 시각 경계", "desc",
                                "나머지 " + String.format("%.1f%%", (1.0 - topProb) * 100) + "의 확률로 예상이 빗나갈 수 있습니다.");
                List<String> invalidationRules = Collections
                                .singletonList("임계값(" + threshold + ") 돌파 실패 시 추세 반전으로 판단하세요.");

                List<Map<String, Object>> policyCheckpoints = Collections
                                .singletonList(Map.of("type", "POLICY", "target",
                                                result.path("targetTicker").asText("QQQ") + " 모니터링", "baseline",
                                                "15일(Horizon) 추세 유지 여부"));
                List<Map<String, Object>> marketIndicators = Collections
                                .singletonList(Map.of("indicator", "모델 정확도", "threshold",
                                                String.format("%.1f%%", accuracy * 100)));
                String notifyRules = "다음 모델 학습 시점(자동 트리거)에 변경된 시그널을 알려드립니다.";
                Map<String, Object> systemStatus = Map.of("crawledAt", generatedAt, "modelStatus",
                                result.path("modelVersion").asText("VERIFIED"));
                String disclaimer = "본 분석 결과는 data-ml 파이프라인의 데이터 패턴 기반 예측이며, 실제 투자 결과에 대한 책임은 투자자 본인에게 있습니다.";

                Map<String, Object> response = new HashMap<>();
                response.put("id", id.startsWith("EVT-") ? id : "EVT-" + id);
                response.put("fastInterpretation",
                                Map.of("badge", decisionBadge, "impactZone", impactZone, "keyReason", keyReason,
                                                "keyNumbers",
                                                keyNumbers, "revisitTime", revisitTime, "weakeningCondition",
                                                weakeningCondition,
                                                "behaviorTips", behaviorTips));
                response.put("detailedEvidence", Map.of("impactPath", impactPath, "refinedEvidence", refinedEvidence,
                                "counterArguments", counterArguments, "invalidationRules", invalidationRules));
                response.put("checkpointMetadata", Map.of("policyCheckpoints", policyCheckpoints, "marketIndicators",
                                marketIndicators, "notifyRules", notifyRules, "systemStatus", systemStatus,
                                "disclaimer", disclaimer));

                return response;
        }
}
