package com.project.server.service.insight;

import com.project.server.domain.PolicyEventEntity;
import com.project.server.dto.InsightDto;
import com.project.server.repository.PolicyEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InsightService {

    private final PolicyEventJpaRepository policyEventJpaRepository;

    public List<String> getViewTabs() {
        return List.of("heatmap", "ranking", "network");
    }

    public List<String> getCountryFilters() {
        return List.of("all", "us", "kr");
    }

    public List<String> getColumns() {
        return List.of("주식", "장기채", "달러", "금");
    }

    public List<InsightDto.HeatmapRow> getRows(String marketScope, String country) {
        List<PolicyEventEntity> latestEvents = policyEventJpaRepository.findTop20ByOrderByCreatedAtDesc();
        return buildRows(latestEvents);
    }

    public List<String> getLegend() {
        return List.of("매우높음", "높음", "보통", "낮음");
    }

    public InsightDto.HeatmapResponse getHeatmap(String marketScope, String country) {
        return InsightDto.HeatmapResponse.builder()
                .viewTabs(getViewTabs())
                .countryFilters(getCountryFilters())
                .columns(getColumns())
                .rows(getRows(marketScope, country))
                .legend(getLegend())
                .build();
    }

    private List<InsightDto.HeatmapRow> buildRows(List<PolicyEventEntity> events) {
        if (events.isEmpty()) {
            return List.of(
                    InsightDto.HeatmapRow.builder()
                            .eventType("데이터대기")
                            .cells(List.of("보통", "보통", "보통", "보통"))
                            .build()
            );
        }

        Map<String, List<PolicyEventEntity>> grouped = events.stream()
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

        return rows.stream().limit(5).toList();
    }

    private List<String> buildCells(double avgImpact, String eventType) {
        int seed = Math.abs(eventType.hashCode() % 12);
        double stock = avgImpact + (seed % 4 - 1) * 4.0;
        double bond = avgImpact + ((seed + 1) % 4 - 1) * 5.0;
        double dollar = avgImpact + ((seed + 2) % 4 - 1) * 3.0;
        double gold = avgImpact + ((seed + 3) % 4 - 1) * 2.0;

        return List.of(level(stock), level(bond), level(dollar), level(gold));
    }

    private String level(double score) {
        if (score >= 75) {
            return "매우높음";
        }
        if (score >= 60) {
            return "높음";
        }
        if (score >= 40) {
            return "보통";
        }
        return "낮음";
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
