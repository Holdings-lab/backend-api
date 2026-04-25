package com.project.server.controller;

import com.project.server.dto.InsightDto;
import com.project.server.service.insight.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @GetMapping("/heatmap")
    public ResponseEntity<InsightDto.HeatmapResponse> getHeatmap(
            @RequestParam(name = "marketScope", defaultValue = "all") String marketScope,
            @RequestParam(name = "country", defaultValue = "all") String country
    ) {
        return ResponseEntity.ok(insightService.getHeatmap(marketScope, country));
    }

    @GetMapping("/view-tabs")
    public ResponseEntity<java.util.List<String>> getViewTabs() {
        return ResponseEntity.ok(insightService.getViewTabs());
    }

    @GetMapping("/country-filters")
    public ResponseEntity<java.util.List<String>> getCountryFilters() {
        return ResponseEntity.ok(insightService.getCountryFilters());
    }

    @GetMapping("/columns")
    public ResponseEntity<java.util.List<String>> getColumns() {
        return ResponseEntity.ok(insightService.getColumns());
    }

    @GetMapping("/rows")
    public ResponseEntity<java.util.List<InsightDto.HeatmapRow>> getRows(
            @RequestParam(name = "marketScope", defaultValue = "all") String marketScope,
            @RequestParam(name = "country", defaultValue = "all") String country
    ) {
        return ResponseEntity.ok(insightService.getRows(marketScope, country));
    }

    @GetMapping("/legend")
    public ResponseEntity<java.util.List<String>> getLegend() {
        return ResponseEntity.ok(insightService.getLegend());
    }
}
