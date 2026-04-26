package com.project.server.controller;

import com.project.server.service.event.SignalService;
import com.project.server.service.portfolio.PortfolioService;
import com.project.server.exception.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class PortfolioController {
    private final SignalService signalService;
    private final PortfolioService portfolioService;

    public PortfolioController(SignalService signalService, PortfolioService portfolioService) {
        this.signalService = signalService;
        this.portfolioService = portfolioService;
    }

    @GetMapping("/portfolio")
    public ResponseEntity<Map<String, Object>> getPortfolioDetail(@RequestParam(defaultValue = "사용자") String user) {
        if (user == null || user.trim().equals("unknown")) {
            throw ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND");
        }
        if (user.trim().equals("empty")) {
            throw ApiException.notFound("포트폴리오 데이터가 존재하지 않습니다.", "PORTFOLIO_NOT_FOUND");
        }

        List<String> mockAssets = Arrays.asList("QQQ", "AAPL", "TSLA");
        String mockEvent = "EVT-001";
        Map<String, Object> metrics = signalService.calculateSignalMetrics(mockEvent);
        Map<String, Object> portfolio = portfolioService.aggregatePortfolio(user);
        Map<String, Object> risk = portfolioService.assessPortfolioRisk(metrics);
        Map<String, Object> themeExposure = portfolioService.classifyThemeExposure(mockAssets, mockEvent);

        Map<String, Object> response = new HashMap<>();
        response.put("summary", portfolio);
        response.put("riskAnalysis", risk);
        response.put("themeExposure", themeExposure);
        return ResponseEntity.ok(response);
    }
}
