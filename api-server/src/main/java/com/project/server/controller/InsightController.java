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
}
