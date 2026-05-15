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
    public ResponseEntity<InsightDto.HeatmapResponse> getHeatmap() {
        return ResponseEntity.ok(insightService.getHeatmap());
    }

    @GetMapping("/tabs")
    public ResponseEntity<java.util.List<String>> getViewTabs() {
        return ResponseEntity.ok(insightService.getViewTabs());
    }

    @GetMapping("/heatmap/columns")
    public ResponseEntity<java.util.List<String>> getColumns() {
        return ResponseEntity.ok(insightService.getColumns());
    }

    @GetMapping("/heatmap/rows")
    public ResponseEntity<java.util.List<InsightDto.HeatmapRow>> getRows() {
        return ResponseEntity.ok(insightService.getRows());
    }

    @GetMapping("/heatmap/legend")
    public ResponseEntity<java.util.List<String>> getLegend() {
        return ResponseEntity.ok(insightService.getLegend());
    }
}
