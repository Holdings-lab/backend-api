package com.project.server.controller;

import com.project.server.service.event.SignalService;
import com.project.server.service.portfolio.PortfolioService;
import com.project.server.service.auth.WatchAssetSelectionService;
import com.project.server.exception.ApiException;
import com.project.server.repository.PolicyEventJpaRepository;
import com.project.server.repository.UserJpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
public class PortfolioController {
    private final PortfolioService portfolioService;
    private final WatchAssetSelectionService watchAssetSelectionService;
    private final PolicyEventJpaRepository policyEventRepository;
    private final UserJpaRepository userJpaRepository;

    public PortfolioController(PortfolioService portfolioService,
                               WatchAssetSelectionService watchAssetSelectionService,
                               PolicyEventJpaRepository policyEventRepository,
                               UserJpaRepository userJpaRepository) {
        this.portfolioService = portfolioService;
        this.watchAssetSelectionService = watchAssetSelectionService;
        this.policyEventRepository = policyEventRepository;
        this.userJpaRepository = userJpaRepository;
    }

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<Map<String, Object>> getPortfolioDetail(@PathVariable Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("올바르지 않은 사용자 ID입니다.", "AUTH_INVALID_USER_ID");
        }

        // 사용자 정보 조회
        String nickname = userJpaRepository.findById(userId)
                .map(user -> user.getNickname() != null ? user.getNickname() : user.getEmail())
                .orElse("사용자");

        // 사용자의 선택된 자산 목록 가져오기
        List<String> userAssets = watchAssetSelectionService.getSelectedAssets(userId)
                .stream()
                .map(asset -> asset.getAssetName())
                .collect(Collectors.toList());
        
        if (userAssets.isEmpty()) {
            userAssets = Arrays.asList("QQQ", "AAPL", "TSLA");
        }

        // 최신 시그널 ID 가져오기
        String latestSignalId = policyEventRepository.findTopByOrderByCreatedAtDesc()
                .map(event -> String.format("EVT-%06d", event.getId()))
                .orElse("EVT-000001");

        Map<String, Object> portfolio = portfolioService.aggregatePortfolio(userId);
        Map<String, Object> risk = portfolioService.assessPortfolioRisk(null);
        Map<String, Object> themeExposure = portfolioService.classifyThemeExposure(userAssets, latestSignalId);

        Map<String, Object> response = new HashMap<>();
        response.put("userId", userId);
        response.put("nickname", nickname);
        response.put("summary", portfolio);
        response.put("riskAnalysis", risk);
        response.put("themeExposure", themeExposure);
        return ResponseEntity.ok(response);
    }
}
