package com.project.server.service.insight;

import com.project.server.domain.PolicyEventEntity;
import com.project.server.dto.InsightDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.PolicyEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class InsightService {
    private static final List<String> DEFAULT_COLUMNS = List.of("주식", "장기채", "달러", "금");
    private static final List<String> DEFAULT_LEGEND = List.of("매우높음", "높음", "보통", "낮음");

    private final PolicyEventJpaRepository policyEventJpaRepository;
    private final com.project.server.service.integration.MlPredictionProxyService mlPredictionProxyService;

    @Value("${insight.heatmap.impact-scale-factor:10.0}")
    private double impactScaleFactor;

    @Value("${insight.heatmap.ml-policy-weight:100.0}")
    private double mlPolicyWeight;

    @Value("${insight.heatmap.threshold.very-high:80.0}")
    private double thresholdVeryHigh;

    @Value("${insight.heatmap.threshold.high:60.0}")
    private double thresholdHigh;

    @Value("${insight.heatmap.threshold.medium:40.0}")
    private double thresholdMedium;

    public InsightDto.HeatmapResponse getHeatmap() {
        return InsightDto.HeatmapResponse.builder()
                .viewTabs(getViewTabs())
                .columns(getColumns())
                .rows(getRows())
                .legend(getLegend())
                .build();
    }

    public List<String> getViewTabs() {
        return List.of("heatmap", "ranking", "network");
    }

    public List<String> getColumns() {
        try {
            // Try to infer columns from ML predictions (if available) or from model metadata.
            JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
            Set<String> cols = new LinkedHashSet<>();

            if (mlResult != null && !mlResult.isMissingNode()) {
                JsonNode preds = mlResult.path("predictions");
                if (preds.isArray() && preds.size() > 0) {
                    for (JsonNode p : preds) {
                        String asset = p.path("asset").asText("");
                        if (!asset.isBlank()) {
                            cols.add(mapAssetToColumn(asset));
                        }
                        if (cols.size() >= 4) break;
                    }
                }

                if (cols.size() < 4) {
                    JsonNode bestFeatures = mlResult.path("bestFeatures");
                    if (bestFeatures.isArray()) {
                        for (JsonNode f : bestFeatures) {
                            String mapped = mapFeatureToColumn(f.asText(""));
                            if (mapped != null) {
                                cols.add(mapped);
                            }
                            if (cols.size() >= 4) break;
                        }
                    }
                }
            }

            if (cols.size() < 4) {
                cols.addAll(DEFAULT_COLUMNS);
            }

            return new ArrayList<>(cols).stream().limit(4).collect(Collectors.toList());
        } catch (Exception e) {
            throw ApiException.internalServerError("히트맵 컬럼 조회 중 오류가 발생했습니다.", "INSIGHT_COLUMNS_LOAD_ERROR");
        }
    }

    public List<InsightDto.HeatmapRow> getRows() {
        try {
            List<PolicyEventEntity> latestEvents = policyEventJpaRepository.findTop20ByOrderByCreatedAtDesc();
            if (latestEvents == null || latestEvents.isEmpty()) {
                return buildEmptyRows();
            }
            return buildRows(latestEvents);
        } catch (Exception e) {
            throw ApiException.internalServerError("히트맵 행 데이터 조회 중 오류가 발생했습니다.", "INSIGHT_ROWS_LOAD_ERROR");
        }
    }

    public List<String> getLegend() {
        // Legend labels are qualitative; return default ordering but allow ML to influence wording in future.
        return DEFAULT_LEGEND;
    }

    private List<InsightDto.HeatmapRow> buildRows(List<PolicyEventEntity> events) {
        if (events == null || events.isEmpty()) {
            return buildEmptyRows();
        }

        try {
            Map<String, List<PolicyEventEntity>> grouped = events.stream()
                    .filter(event -> event != null)
                    .collect(Collectors.groupingBy(event -> normalizeEventType(event.getKeyword())));

            List<InsightDto.HeatmapRow> rows = new ArrayList<>();
            for (Map.Entry<String, List<PolicyEventEntity>> entry : grouped.entrySet()) {
                double avgImpact = entry.getValue().stream()
                        .map(PolicyEventEntity::getImpactScore)
                        .filter(score -> score != null)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(50.0);

                rows.add(InsightDto.HeatmapRow.builder()
                        .eventType(entry.getKey())
                        .cells(buildCells(avgImpact, entry.getKey()))
                        .build());
            }

            if (rows.isEmpty()) {
                return buildEmptyRows();
            }

            return rows.stream().limit(5).toList();
        } catch (Exception e) {
            throw ApiException.internalServerError("히트맵 행 데이터 조회 중 오류가 발생했습니다.", "INSIGHT_ROWS_LOAD_ERROR");
        }
    }

    private List<String> buildCells(double avgImpact, String eventType) {
        try {
            JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();
            double mlImpact = avgImpact * impactScaleFactor;
            if (mlResult != null && !mlResult.isMissingNode()) {
                double policyScore = mlResult.path("metrics").path("policyScore").asDouble(0.0);
                // Apply tunable policy-score contribution.
                mlImpact += (policyScore * mlPolicyWeight);
            }

            int seed = Math.abs(eventType.hashCode() % 12);
            double stock = clampToPercentRange(mlImpact + (seed % 4 - 1) * 4.0);
            double bond = clampToPercentRange(mlImpact + ((seed + 1) % 4 - 1) * 5.0);
            double dollar = clampToPercentRange(mlImpact + ((seed + 2) % 4 - 1) * 3.0);
            double gold = clampToPercentRange(mlImpact + ((seed + 3) % 4 - 1) * 2.0);

            return List.of(level(stock), level(bond), level(dollar), level(gold));
        } catch (Exception e) {
            throw ApiException.internalServerError("히트맵 셀 생성 중 오류가 발생했습니다.", "INSIGHT_CELLS_BUILD_ERROR");
        }
    }

    private String level(double score) {
        if (score >= thresholdVeryHigh) {
            return "매우높음";
        }
        if (score >= thresholdHigh) {
            return "높음";
        }
        if (score >= thresholdMedium) {
            return "보통";
        }
        return "낮음";
    }

    private String mapAssetToColumn(String asset) {
        if (asset == null) return "주식";
        String a = asset.toUpperCase(Locale.ROOT);
        if (a.contains("TLT") || a.contains("BOND") || a.contains("GOV") || a.contains("T10") || a.contains("BND")) {
            return "장기채";
        }
        if (a.contains("USD") || a.contains("DXY") || a.contains("USDX") || a.contains("DOLLAR") || a.contains("달러")) {
            return "달러";
        }
        if (a.contains("GLD") || a.contains("GOLD") || a.contains("XAU")) {
            return "금";
        }
        return "주식";
    }

    private String mapFeatureToColumn(String feature) {
        if (feature == null || feature.isBlank()) {
            return null;
        }
        String f = feature.toLowerCase(Locale.ROOT);
        if (f.contains("dollar") || f.contains("usd") || f.contains("dxy") || f.contains("환율") || f.contains("달러")) {
            return "달러";
        }
        if (f.contains("gold") || f.contains("xau") || f.contains("금")) {
            return "금";
        }
        if (f.contains("bond") || f.contains("yield") || f.contains("rate") || f.contains("채") || f.contains("금리")) {
            return "장기채";
        }
        if (f.contains("qqq") || f.contains("nasdaq") || f.contains("equity") || f.contains("stock") || f.contains("주식")) {
            return "주식";
        }
        return null;
    }

    private double clampToPercentRange(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private List<InsightDto.HeatmapRow> buildEmptyRows() {
        return List.of(
                InsightDto.HeatmapRow.builder()
                        .eventType("조회된 정책 없음")
                        .cells(List.of())
                        .build()
        );
    }

    private String normalizeEventType(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "정책";
        }
        String normalized = keyword.toLowerCase(Locale.ROOT);
        if (normalized.contains("rate") || normalized.contains("금리")) {
            return "금리";
        }
        if (normalized.contains("inflation") || normalized.contains("물가")) {
            return "물가";
        }
        if (normalized.contains("employment") || normalized.contains("고용")) {
            return "고용";
        }
        if (normalized.contains("fx") || normalized.contains("달러") || normalized.contains("환율")) {
            return "환율";
        }
        return "정책";
    }
}
