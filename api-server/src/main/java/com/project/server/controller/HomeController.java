package com.project.server.controller;

import com.project.server.dto.HomeBriefingDto;
import com.project.server.dto.HomeDto;
import com.project.server.service.home.HomeBriefingService;
import com.project.server.service.home.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;
    private final HomeBriefingService homeBriefingService;

    @GetMapping("/home")
    public ResponseEntity<HomeDto.HomeResponse> getHome(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeService.getHome(userId));
    }

    @GetMapping("/home/briefing")
    public ResponseEntity<HomeBriefingDto.BriefingResponse> getHomeBriefing(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getBriefing(userId));
    }

    @GetMapping("/home/header")
    public ResponseEntity<HomeBriefingDto.HomeHeader> getHomeHeader(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getHomeHeader(userId));
    }

    @GetMapping("/home/featured-card")
    public ResponseEntity<HomeBriefingDto.FeaturedSignalCard> getFeaturedCard(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getFeaturedCard(userId));
    }

    @GetMapping("/home/portfolio-card")
    public ResponseEntity<HomeBriefingDto.PortfolioCard> getPortfolioCard(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getPortfolioCard(userId));
    }

    @GetMapping("/home/secondary-signals")
    public ResponseEntity<java.util.List<HomeBriefingDto.SecondarySignalItem>> getSecondarySignals(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getSecondarySignals(userId));
    }

    @GetMapping("/home/quick-interpretation")
    public ResponseEntity<HomeBriefingDto.QuickInterpretation> getQuickInterpretation(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getQuickInterpretation(userId));
    }

    @GetMapping("/home/detail-tabs")
    public ResponseEntity<HomeBriefingDto.DetailTabs> getDetailTabs(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getDetailTabs(userId));
    }

    @GetMapping("/home/checkpoint-tab")
    public ResponseEntity<HomeBriefingDto.CheckpointTab> getCheckpointTab(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getCheckpointTab(userId));
    }

    @GetMapping("/home/disclaimer")
    public ResponseEntity<HomeBriefingDto.DisclaimerResponse> getDisclaimer(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(homeBriefingService.getDisclaimer(userId));
    }
}
