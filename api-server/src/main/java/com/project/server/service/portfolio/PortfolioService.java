package com.project.server.service.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.service.integration.MlPredictionProxyService;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class PortfolioService {

    private final MlPredictionProxyService mlPredictionProxyService;

    public PortfolioService(MlPredictionProxyService mlPredictionProxyService) {
        this.mlPredictionProxyService = mlPredictionProxyService;
    }

    public Map<String, Object> aggregatePortfolio(Long userId) {
        JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
        double policyScore = 0.0;
        if (mlResult != null && !mlResult.isMissingNode()) {
            policyScore = mlResult.path("metrics").path("policyScore").asDouble(0.0);
        }
        
        // 포트폴리오 수익률을 ML 모델의 기대수익률(policyScore)을 반영하여 동적으로 계산
        double baseReturn = 1.2;
        double adjustedReturn = Math.round((baseReturn + (policyScore * 100)) * 100.0) / 100.0;
        double returnAmount = Math.round(15000000 * (adjustedReturn / 100.0));
        
        return Map.of("totalAssets", 15000000, "dailyReturnRate", adjustedReturn, "dailyReturnAmount", returnAmount);
    }

    public Map<String, Object> assessPortfolioRisk(Map<String, Object> metrics) {
        JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
        if (mlResult == null || mlResult.isMissingNode()) {
            return Map.of("level", "UNKNOWN", "description", "ML 데이터를 불러올 수 없습니다.");
        }
        
        double threshold = mlResult.path("bestThreshold").asDouble(0.004);
        double policyScore = mlResult.path("metrics").path("policyScore").asDouble(0.0);
        String topLabel = mlResult.path("metrics").path("topLabel").asText("flat");
        
        String level = "SAFE";
        String description = "포트폴리오가 안정적인 상태입니다.";
        
        if (policyScore < -threshold) {
            level = "DANGER";
            description = "시장 하방 압력(" + topLabel + ")이 거세어 포트폴리오의 리스크가 높습니다. 현금 비중 확대를 권장합니다.";
        } else if (policyScore > threshold) {
            level = "SAFE";
            description = "상승 동력(" + topLabel + ")이 포착되었습니다. 현재 포지션을 유지하거나 비중 확대를 고려하세요.";
        } else {
            level = "WARNING";
            description = "방향성이 뚜렷하지 않은 혼조세(" + topLabel + ")입니다. 자산별 노출도를 점검하세요.";
        }
        
        return Map.of("level", level, "description", description);
    }

    public Map<String, Object> classifyThemeExposure(List<String> assets, String event) {
        JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
        if (mlResult == null || mlResult.isMissingNode()) {
            return Map.of("theme", "알 수 없음", "exposurePercent", 0.0);
        }
        
        String targetTicker = mlResult.path("targetTicker").asText("QQQ");
        double topProb = mlResult.path("metrics").path("topLabelProbability").asDouble(0.0);
        
        String theme = targetTicker.equals("QQQ") ? "기술주/AI" : targetTicker;
        return Map.of("theme", theme, "exposurePercent", Math.round(topProb * 1000) / 10.0);
    }
}
