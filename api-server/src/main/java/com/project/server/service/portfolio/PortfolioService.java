package com.project.server.service.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.service.integration.MlPredictionProxyService;
import com.project.server.service.asset.AssetMetricsService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PortfolioService {

    private final MlPredictionProxyService mlPredictionProxyService;
    private final AssetMetricsService assetMetricsService;

    public PortfolioService(MlPredictionProxyService mlPredictionProxyService,
                            AssetMetricsService assetMetricsService) {
        this.mlPredictionProxyService = mlPredictionProxyService;
        this.assetMetricsService = assetMetricsService;
    }

    public Map<String, Object> aggregatePortfolio(Long userId) {
        AssetMetricsService.AssetMetrics metrics = assetMetricsService.compute(userId);
        BigDecimal totalAssets = metrics.assetTotal();
        BigDecimal dailyReturnRate = metrics.dailyChangePct();
        BigDecimal dailyReturnAmount = totalAssets.multiply(dailyReturnRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalAssets", totalAssets);
        summary.put("dailyReturnRate", dailyReturnRate);
        summary.put("dailyReturnAmount", dailyReturnAmount);
        return summary;
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
