package com.project.server.controller;

import com.project.server.dto.HomeBriefingDto;
import com.project.server.dto.HomeDto;
import com.project.server.service.home.HomeBriefingService;
import com.project.server.service.home.HomeService;
import lombok.RequiredArgsConstructor;
import com.project.server.service.user.UserService;
import com.project.server.service.event.SignalService;
import com.project.server.service.portfolio.PortfolioService;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final HomeBriefingService homeBriefingService;
    private final UserService userService;
    private final SignalService signalService;
    private final PortfolioService portfolioService;

    @GetMapping("/home")
    public ResponseEntity<Map<String, Object>> getHomeIntegrated(@RequestParam(name = "userId", required = true) Long userId) {
        // 사용자 선택 자산 조회 (추후 실제 데이터 연동 필요)
        List<String> userAssets = Arrays.asList("QQQ", "AAPL", "TSLA");
        
        String greeting = userService.getUserGreeting(userId);
        String profileInitial = userService.getProfileInitial(userId);
        Map<String, Object> portfolio = portfolioService.aggregatePortfolio(userId);
        List<Map<String, Object>> rawSignals = signalService.collectPolicyNewsRaw();
        String primaryEventId = "EVT-000001"; // 추후 최신 이벤트로 연동
        double sensitivity = signalService.calculateAssetSensitivity(userAssets);
        Map<String, Object> metrics = signalService.calculateSignalMetrics(primaryEventId);
        Map<String, Object> rankInfo = signalService.rankSignalsForUser(userAssets, rawSignals);
        String formattedTitle = signalService.formatSignalTitle((String) rawSignals.get(0).get("rawTitle"));
        double exposurePercent = signalService.calculateExposurePercent(userAssets, sensitivity);
        Map<String, Object> actionPlan = signalService.generateActionPlan(metrics);
        Map<String, Object> risk = portfolioService.assessPortfolioRisk(metrics);
        Map<String, Object> themeExposure = portfolioService.classifyThemeExposure(userAssets, primaryEventId);
        List<Map<String, Object>> secondarySignals = signalService.getSecondarySignals(rawSignals);

        Map<String, Object> response = new HashMap<>();
        response.put("user", Map.of("greeting", greeting, "profileInitial", profileInitial));
        response.put("portfolio", portfolio);
        response.put("portfolioRisk", risk);
        response.put("themeExposure", themeExposure);
        response.put("primarySignal", Map.of("title", formattedTitle, "exposurePercent", exposurePercent, "actionPlan", actionPlan, "rankInfo", rankInfo));
        response.put("secondarySignals", secondarySignals);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/home/briefing")
        public ResponseEntity<HomeBriefingDto.BriefingResponse> getHomeBriefing(
            @RequestParam(name = "userId", required = true) Long userId
        ) {
        return ResponseEntity.ok(homeBriefingService.getBriefing(userId));
    }

    @GetMapping("/home/header")
        public ResponseEntity<HomeBriefingDto.HomeHeader> getHomeHeader(
            @RequestParam(name = "userId", required = true) Long userId
        ) {
        return ResponseEntity.ok(homeBriefingService.getHomeHeader(userId));
    }

    @GetMapping("/home/featured-card")
        public ResponseEntity<HomeBriefingDto.FeaturedSignalCard> getFeaturedCard(
            @RequestParam(name = "userId", required = true) Long userId
        ) {
        return ResponseEntity.ok(homeBriefingService.getFeaturedCard(userId));
    }

    @GetMapping("/home/portfolio-card")
        public ResponseEntity<HomeBriefingDto.PortfolioCard> getPortfolioCard(
            @RequestParam(name = "userId", required = true) Long userId
        ) {
        return ResponseEntity.ok(homeBriefingService.getPortfolioCard(userId));
    }

    @GetMapping("/home/secondary-signals")
        public ResponseEntity<java.util.List<HomeBriefingDto.SecondarySignalItem>> getSecondarySignals(
            @RequestParam(name = "userId", required = true) Long userId
        ) {
        return ResponseEntity.ok(homeBriefingService.getSecondarySignals(userId));
    }

    @GetMapping("/home/quick-interpretation")
        public ResponseEntity<HomeBriefingDto.QuickInterpretation> getQuickInterpretation(
            @RequestParam(name = "userId", required = true) Long userId
        ) {
        return ResponseEntity.ok(homeBriefingService.getQuickInterpretation(userId));
    }

    @GetMapping("/home/detail-tabs")
        public ResponseEntity<HomeBriefingDto.DetailTabs> getDetailTabs(
            @RequestParam(name = "userId", required = true) Long userId
        ) {
        return ResponseEntity.ok(homeBriefingService.getDetailTabs(userId));
    }

    @GetMapping("/home/checkpoint-tab")
        public ResponseEntity<HomeBriefingDto.CheckpointTab> getCheckpointTab(
            @RequestParam(name = "userId", required = true) Long userId
        ) {
        return ResponseEntity.ok(homeBriefingService.getCheckpointTab(userId));
    }

    @GetMapping("/home/disclaimer")
    public ResponseEntity<HomeBriefingDto.DisclaimerResponse> getDisclaimer(
            @RequestParam(name = "userId", required = true) Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getDisclaimer(userId));
    }
}
