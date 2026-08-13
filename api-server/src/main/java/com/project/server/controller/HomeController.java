package com.project.server.controller;

import com.project.server.dto.HomeBriefingDto;
import com.project.server.security.CurrentUserId;
import com.project.server.service.home.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    /**
     * 사용자의 홈 화면 통합 조회
     * 사용자의 프로필, 포트폴리오, 감시 자산, 정책 신호 등의 전체 홈 데이터를 반환합니다.
     */
    @GetMapping
    public ResponseEntity<com.project.server.dto.HomeDto.HomeResponse> getHomeIntegrated(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getHome(userId));
    }

    /**
     * 홈 브리핑 통합 조회
     * 주요 신호, 포트폴리오, 보조 신호, 분석, 체크포인트 등을 포함한 상세 브리핑 데이터를 반환합니다.
     */
    @GetMapping("/briefing")
    public ResponseEntity<HomeBriefingDto.BriefingResponse> getHomeBriefing(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getBriefing(userId));
    }

    /**
     * 홈 헤더 조회
     * 인사말, 사용자명, 프로필 초성 정보를 반환합니다.
     */
    @GetMapping("/header")
    public ResponseEntity<HomeBriefingDto.HomeHeader> getHomeHeader(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getHomeHeader(userId));
    }

    /**
     * 주요 정책 신호 카드 조회
     * 현재 최우선 정책 이벤트의 신호 강도, 판단, 자산 노출도, 상승/하락 확률 등을 반환합니다.
     */
    @GetMapping("/cards/featured")
    public ResponseEntity<HomeBriefingDto.FeaturedSignalCard> getFeaturedCard(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getFeaturedCard(userId));
    }

    /**
     * 포트폴리오 카드 조회
     * 총 자산, 일일 수익률, 현재 위험 등급, 테마별 노출도를 반환합니다.
     */
    @GetMapping("/cards/portfolio")
    public ResponseEntity<HomeBriefingDto.PortfolioCard> getPortfolioCard(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getPortfolioCard(userId));
    }

    /**
     * 보조 정책 신호 목록 조회
     * 주요 신호 외 추가 정책 이벤트들의 신호 목록을 반환합니다.
     */
    @GetMapping("/signals/secondary")
    public ResponseEntity<java.util.List<HomeBriefingDto.SecondarySignalItem>> getSecondarySignals(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getSecondarySignals(userId));
    }

    /**
     * 빠른 해석 조회
     * 현재 최우선 정책 이벤트의 빠른 해석 데이터를 반환합니다.
     */
    @GetMapping("/interpretations/quick")
    public ResponseEntity<HomeBriefingDto.QuickInterpretation> getQuickInterpretation(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getQuickInterpretation(userId));
    }

    /**
     * 상세 분석 탭 조회
     * 요약, 증거, 반박, 무효화 조건 등 신호에 대한 심화 분석을 반환합니다.
     */
    @GetMapping("/tabs/details")
    public ResponseEntity<HomeBriefingDto.DetailTabs> getDetailTabs(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getDetailTabs(userId));
    }

    /**
     * 체크포인트 탭 조회
     * 현재 최우선 정책 이벤트의 체크포인트 데이터를 반환합니다.
     */
    @GetMapping("/tabs/checkpoints")
    public ResponseEntity<HomeBriefingDto.CheckpointTab> getCheckpointTab(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getCheckpointTab(userId));
    }

    /**
     * 법적 고지 조회
     * 홈 화면에 표시되는 법적 고지 데이터를 반환합니다.
     */
    @GetMapping("/disclaimer")
    public ResponseEntity<HomeBriefingDto.DisclaimerResponse> getDisclaimer(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(homeService.getDisclaimer(userId));
    }
}
