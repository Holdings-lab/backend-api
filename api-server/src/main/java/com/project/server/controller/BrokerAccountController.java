package com.project.server.controller;

import com.project.server.dto.BrokerAccountDto;
import com.project.server.security.CurrentUserId;
import com.project.server.service.broker.AssetSyncService;
import com.project.server.service.broker.BrokerAccountService;
import com.project.server.service.broker.PortfolioAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BrokerAccountController {

    private final BrokerAccountService brokerAccountService;
    private final AssetSyncService assetSyncService;
    private final PortfolioAggregationService portfolioAggregationService;

    /**
     * 증권사 계좌 연동.
     * 인증 토큰의 userId = 앱 사용자 ID, body hyphenUserId/hyphenUserPw = 증권사 로그인 자격증명.
     */
    @PostMapping("/accounts")
    public ResponseEntity<List<BrokerAccountDto.BrokerAccountResponse>> linkAccount(
            @CurrentUserId Long userId,
            @RequestBody BrokerAccountDto.LinkRequest request) {
        return ResponseEntity.ok(brokerAccountService.linkAccounts(userId, request));
    }

    /** 사용자 계좌 목록 조회 */
    @GetMapping("/accounts")
    public ResponseEntity<List<BrokerAccountDto.BrokerAccountResponse>> getUserAccounts(
            @CurrentUserId Long userId) {
        List<BrokerAccountDto.BrokerAccountResponse> response = brokerAccountService.getUserAccounts(userId);
        return ResponseEntity.ok(response);
    }

    /** 계좌 상세 조회 */
    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<BrokerAccountDto.BrokerAccountDetailResponse> getAccount(
            @CurrentUserId Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.BrokerAccountDetailResponse response = brokerAccountService.getAccount(userId, accountId);
        return ResponseEntity.ok(response);
    }

    /** 계좌 연동 해제 */
    @DeleteMapping("/accounts/{accountId}")
    public ResponseEntity<BrokerAccountDto.UnlinkAccountResponse> unlinkAccount(
            @CurrentUserId Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.UnlinkAccountResponse response = brokerAccountService.unlinkAccount(userId, accountId);
        return ResponseEntity.ok(response);
    }

    /** 기본 계좌 설정 */
    @PutMapping("/accounts/{accountId}/primary")
    public ResponseEntity<BrokerAccountDto.SetPrimaryAccountResponse> setPrimaryAccount(
            @CurrentUserId Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.SetPrimaryAccountResponse response = brokerAccountService.setPrimaryAccount(userId, accountId);
        return ResponseEntity.ok(response);
    }

    /** 계좌 동기화 요청 */
    @PostMapping("/accounts/{accountId}/sync")
    public ResponseEntity<BrokerAccountDto.SyncResponse> requestSync(
            @CurrentUserId Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.SyncResponse response = assetSyncService.requestSync(userId, accountId);
        return ResponseEntity.ok(response);
    }

    /** 계좌 동기화 상태 조회 */
    @GetMapping("/accounts/{accountId}/sync/{syncId}")
    public ResponseEntity<BrokerAccountDto.SyncStatusResponse> getSyncStatus(
            @CurrentUserId Long userId,
            @PathVariable Long accountId,
            @PathVariable Long syncId) {
        BrokerAccountDto.SyncStatusResponse response = assetSyncService.getSyncStatus(userId, accountId, syncId);
        return ResponseEntity.ok(response);
    }

    /** 계좌 동기화 이력 조회 */
    @GetMapping("/accounts/{accountId}/sync-history")
    public ResponseEntity<List<BrokerAccountDto.SyncHistoryResponse>> getSyncHistory(
            @CurrentUserId Long userId,
            @PathVariable Long accountId) {
        List<BrokerAccountDto.SyncHistoryResponse> response = assetSyncService.getSyncHistory(userId, accountId);
        return ResponseEntity.ok(response);
    }

    /** 포트폴리오 조회 */
    @GetMapping("/portfolio")
    public ResponseEntity<BrokerAccountDto.CombinedPortfolioResponse> getCombinedPortfolio(
            @CurrentUserId Long userId) {
        BrokerAccountDto.CombinedPortfolioResponse response = portfolioAggregationService
                .getUserCombinedPortfolio(userId);
        return ResponseEntity.ok(response);
    }

    /** 계좌별 포트폴리오 조회 */
    @GetMapping("/accounts/{accountId}/portfolio")
    public ResponseEntity<BrokerAccountDto.AccountPortfolioDto> getAccountPortfolio(
            @CurrentUserId Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.AccountPortfolioDto response = portfolioAggregationService.getAccountPortfolio(userId,
                accountId);
        return ResponseEntity.ok(response);
    }

    /** 자산 배분 분석 */
    @GetMapping("/portfolio/allocation")
    public ResponseEntity<Map<String, Object>> analyzeAssetAllocation(
            @CurrentUserId Long userId) {
        Map<String, Object> response = portfolioAggregationService.analyzeAssetAllocation(userId);
        return ResponseEntity.ok(response);
    }

    /** 성과 분석 */
    @GetMapping("/portfolio/performance")
    public ResponseEntity<Map<String, Object>> analyzePerformance(
            @CurrentUserId Long userId) {
        Map<String, Object> response = portfolioAggregationService.analyzePerformance(userId);
        return ResponseEntity.ok(response);
    }
}
