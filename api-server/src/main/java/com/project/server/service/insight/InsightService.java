package com.project.server.service.insight;

import com.project.server.dto.InsightDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InsightService {

    public InsightDto.HeatmapResponse getHeatmap(String marketScope, String country) {
        return InsightDto.HeatmapResponse.builder()
                .viewTabs(List.of("heatmap", "ranking", "network"))
                .countryFilters(List.of("all", "us", "kr"))
                .columns(List.of("주식", "장기채", "달러", "금"))
                .rows(List.of(
                        InsightDto.HeatmapRow.builder().eventType("금리").cells(List.of("높음", "매우높음", "높음", "보통")).build(),
                        InsightDto.HeatmapRow.builder().eventType("물가").cells(List.of("보통", "높음", "보통", "높음")).build(),
                        InsightDto.HeatmapRow.builder().eventType("고용").cells(List.of("보통", "보통", "높음", "낮음")).build(),
                        InsightDto.HeatmapRow.builder().eventType("환율").cells(List.of("보통", "낮음", "매우높음", "보통")).build(),
                        InsightDto.HeatmapRow.builder().eventType("정책연설").cells(List.of("보통", "보통", "높음", "낮음")).build()
                ))
                .legend(List.of("매우높음", "높음", "보통", "낮음"))
                .build();
    }
}
