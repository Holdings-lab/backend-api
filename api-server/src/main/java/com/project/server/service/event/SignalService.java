package com.project.server.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.service.integration.MlPredictionProxyService;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SignalService {

    private final MlPredictionProxyService mlPredictionProxyService;

    public SignalService(MlPredictionProxyService mlPredictionProxyService) {
        this.mlPredictionProxyService = mlPredictionProxyService;
    }

    public List<Map<String, Object>> collectPolicyNewsRaw() { 
        JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
        String title = "미 연준 50bp 금리 인하 확정"; // Fallback
        if (mlResult != null && !mlResult.isMissingNode()) {
            title = mlResult.path("targetTicker").asText("QQQ") + " 관련 주요 정책/이슈 변동 감지";
        }
        return Collections.singletonList(Map.of("rawTitle", title)); 
    }
    
    public double calculateAssetSensitivity(List<String> assets) { return 1.5; }
    
    public Map<String, Object> calculateSignalMetrics(String event) { 
        JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
        Map<String, Object> metrics = new HashMap<>();
        if (mlResult != null && !mlResult.isMissingNode()) {
            metrics.put("policyScore", mlResult.path("metrics").path("policyScore").asDouble(0.0));
            metrics.put("directionAccuracy", mlResult.path("metrics").path("directionAccuracy").asDouble(0.5));
            metrics.put("topLabelProbability", mlResult.path("metrics").path("topLabelProbability").asDouble(0.0));
        }
        return metrics;
    }
    
    public Map<String, Object> rankSignalsForUser(List<String> assets, List<Map<String, Object>> rawSignals) {
        JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
        double rankScore = 95.5;
        if (mlResult != null && !mlResult.isMissingNode()) {
            rankScore = mlResult.path("metrics").path("directionAccuracy").asDouble(0.5) * 100;
        }
        // primarySignalId는 동적으로 생성 (추후 PolicyEventRepository에서 최신 ID 조회)
        return Map.of("primarySignalId", "EVT-000001", "rankScore", rankScore);
    }
    
    public String formatSignalTitle(String raw) { return raw; }
    
    public double calculateExposurePercent(List<String> assets, double sensitivity) { 
        JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
        if (mlResult != null && !mlResult.isMissingNode()) {
            return Math.round(mlResult.path("metrics").path("topLabelProbability").asDouble(0.0) * 1000) / 10.0;
        }
        return 42.5; 
    }
    
    public Map<String, Object> generateActionPlan(Map<String, Object> metrics) {
        JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
        String action = "방어 비중 점검";
        String description = "변동성이 높으므로 포트폴리오 리밸런싱을 고려하세요.";
        if (mlResult != null && !mlResult.isMissingNode()) {
            double policyScore = mlResult.path("metrics").path("policyScore").asDouble(0.0);
            double threshold = mlResult.path("bestThreshold").asDouble(0.004);
            if (policyScore > threshold) {
                action = "매수 포지션 확대";
                description = "시장 상승 모멘텀이 강합니다. 비중 확대를 고려하세요.";
            } else if (policyScore < -threshold) {
                action = "비중 축소 필요";
                description = "하방 리스크가 큽니다. 현금 비중을 늘리세요.";
            }
        }
        return Map.of("action", action, "description", description);
    }
    
    public List<String> generateReasonCandidates(String event) { 
        JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
        if (mlResult != null && !mlResult.isMissingNode()) {
            JsonNode features = mlResult.path("topFeatureImportance");
            if (features.isArray() && features.size() > 0) {
                List<String> reasons = new ArrayList<>();
                for (JsonNode f : features) {
                    reasons.add(f.path("feature").asText());
                }
                return reasons;
            }
        }
        return Arrays.asList("금리 변동", "고용 지표"); 
    }
    
    public List<Map<String, Object>> getSecondarySignals(List<Map<String, Object>> rawSignals) {
        // 보조 시그널들도 동적으로 생성 (추후 PolicyEventRepository 2-3개 조회)
        return Collections.singletonList(Map.of("id", "EVT-000002", "title", "보조 시그널 대기 중", "exposure", 15.0, "decision", "기다리기"));
    }
}
