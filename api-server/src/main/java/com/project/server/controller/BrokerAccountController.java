package com.project.server.controller;

import com.project.server.dto.BrokerAccountDto;
import com.project.server.service.broker.AssetSyncService;
import com.project.server.service.broker.BrokerAccountService;
import com.project.server.service.broker.PortfolioAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/broker")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BrokerAccountController {

    private final BrokerAccountService brokerAccountService;
    private final AssetSyncService assetSyncService;
    private final PortfolioAggregationService portfolioAggregationService;

    /**
     * 계좌 연동 (OAuth 또는 Direct 방식)
     * - OAuth: connectedId 없음 → /oauth/authorize URL 반환
     * - Direct: connectedId 있음 → 바로 계좌 조회 및 저장
     */
    @PostMapping("/{userId}/accounts")
    public ResponseEntity<?> linkAccount(
            @PathVariable Long userId,
            @RequestBody BrokerAccountDto.LinkRequest request) {
        // connectedId가 있으면 Direct, 없으면 OAuth
        if (request.getConnectedId() != null && !request.getConnectedId().isEmpty()) {
            BrokerAccountDto.BrokerAccountResponse response = brokerAccountService.directLinkAccount(userId, request);
            return ResponseEntity.ok(response);
        } else {
            BrokerAccountDto.AuthResponse response = brokerAccountService.startOAuthLinking(userId, request);
            return ResponseEntity.ok(response);
        }
    }

    /**
     * OAuth 콜백 처리
     */
    @PostMapping("/{userId}/accounts/callback")
    public ResponseEntity<BrokerAccountDto.BrokerAccountResponse> handleAuthCallback(
            @PathVariable Long userId,
            @RequestBody BrokerAccountDto.AuthCallbackRequest request) {
        BrokerAccountDto.BrokerAccountResponse response = brokerAccountService.handleAuthCallback(userId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * 사용자의 모든 연동 계좌 조회
     */
    @GetMapping("/{userId}/accounts")
    public ResponseEntity<List<BrokerAccountDto.BrokerAccountResponse>> getUserAccounts(
            @PathVariable Long userId) {
        List<BrokerAccountDto.BrokerAccountResponse> response = brokerAccountService.getUserAccounts(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 계좌 상세 정보 조회
     */
    @GetMapping("/{userId}/accounts/{accountId}")
    public ResponseEntity<BrokerAccountDto.BrokerAccountDetailResponse> getAccount(
            @PathVariable Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.BrokerAccountDetailResponse response = brokerAccountService.getAccount(accountId);
        return ResponseEntity.ok(response);
    }

    /**
     * 계좌 연동 해제
     */
    @DeleteMapping("/{userId}/accounts/{accountId}")
    public ResponseEntity<BrokerAccountDto.UnlinkAccountResponse> unlinkAccount(
            @PathVariable Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.UnlinkAccountResponse response = brokerAccountService.unlinkAccount(userId, accountId);
        return ResponseEntity.ok(response);
    }

    /**
     * Primary 계좌 설정
     */
    @PutMapping("/{userId}/accounts/{accountId}/primary")
    public ResponseEntity<Void> setPrimaryAccount(
            @PathVariable Long userId,
            @PathVariable Long accountId) {
        brokerAccountService.setPrimaryAccount(userId, accountId);
        return ResponseEntity.ok().build();
    }

    /**
     * 계좌 동기화 요청
     */
    @PostMapping("/{userId}/accounts/{accountId}/sync")
    public ResponseEntity<BrokerAccountDto.SyncResponse> requestSync(
            @PathVariable Long userId,
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "BALANCE,POSITION") String syncType) {
        BrokerAccountDto.SyncResponse response = assetSyncService.requestSync(userId, accountId, syncType);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * 동기화 상태 조회
     */
    @GetMapping("/{userId}/accounts/{accountId}/sync/{syncId}")
    public ResponseEntity<BrokerAccountDto.SyncStatusResponse> getSyncStatus(
            @PathVariable Long userId,
            @PathVariable Long accountId,
            @PathVariable String syncId) {
        BrokerAccountDto.SyncStatusResponse response = assetSyncService.getSyncStatus(userId, accountId, syncId);
        return ResponseEntity.ok(response);
    }

    /**
     * 동기화 이력 조회
     */
    @GetMapping("/{userId}/accounts/{accountId}/sync-history")
    public ResponseEntity<List<BrokerAccountDto.SyncHistoryResponse>> getSyncHistory(
            @PathVariable Long userId,
            @PathVariable Long accountId) {
        List<BrokerAccountDto.SyncHistoryResponse> response = assetSyncService.getSyncHistory(userId, accountId);
        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 통합 포트폴리오 조회
     */
    @GetMapping("/{userId}/portfolio")
    public ResponseEntity<BrokerAccountDto.CombinedPortfolioResponse> getCombinedPortfolio(
            @PathVariable Long userId) {
        BrokerAccountDto.CombinedPortfolioResponse response = portfolioAggregationService.getUserCombinedPortfolio(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 계좌 포트폴리오 조회
     */
    @GetMapping("/{userId}/accounts/{accountId}/portfolio")
    public ResponseEntity<BrokerAccountDto.AccountPortfolioDto> getAccountPortfolio(
            @PathVariable Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.AccountPortfolioDto response = portfolioAggregationService.getAccountPortfolio(userId, accountId);
        return ResponseEntity.ok(response);
    }

    /**
     * 자산 배분 분석
     */
    @GetMapping("/{userId}/portfolio/allocation")
    public ResponseEntity<Map<String, Object>> analyzeAssetAllocation(
            @PathVariable Long userId) {
        Map<String, Object> response = portfolioAggregationService.analyzeAssetAllocation(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 포트폴리오 성과 분석
     */
    @GetMapping("/{userId}/portfolio/performance")
    public ResponseEntity<Map<String, Object>> analyzePerformance(
            @PathVariable Long userId) {
        Map<String, Object> response = portfolioAggregationService.analyzePerformance(userId);
        return ResponseEntity.ok(response);
    }
}
