package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class InsightDto {

    @Data
    @Builder
    public static class HeatmapResponse {
        private List<String> viewTabs;
        private List<String> countryFilters;
        private List<String> columns;
        private List<HeatmapRow> rows;
        private List<String> legend;
    }

    @Data
    @Builder
    public static class HeatmapRow {
        private String eventType;
        private List<String> cells;
    }
}
