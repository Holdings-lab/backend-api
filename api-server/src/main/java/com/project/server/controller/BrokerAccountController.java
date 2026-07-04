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
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BrokerAccountController {

    private final BrokerAccountService brokerAccountService;
    private final AssetSyncService assetSyncService;
    private final PortfolioAggregationService portfolioAggregationService;

    /**
     * 증권사 계좌 연동.
     * path userId = 앱 사용자 ID, body hyphenUserId/hyphenUserPw = 증권사 로그인 자격증명.
     */
    @PostMapping("/{userId}/accounts")
    public ResponseEntity<List<BrokerAccountDto.BrokerAccountResponse>> linkAccount(
            @PathVariable Long userId,
            @RequestBody BrokerAccountDto.LinkRequest request) {
        return ResponseEntity.ok(brokerAccountService.linkAccounts(userId, request));
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
        BrokerAccountDto.BrokerAccountDetailResponse response = brokerAccountService.getAccount(userId, accountId);
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
    public ResponseEntity<BrokerAccountDto.SetPrimaryAccountResponse> setPrimaryAccount(
            @PathVariable Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.SetPrimaryAccountResponse response = brokerAccountService.setPrimaryAccount(userId, accountId);
        return ResponseEntity.ok(response);
    }

    /**
     * 계좌 동기화 요청
     */
    @PostMapping("/{userId}/accounts/{accountId}/sync")
    public ResponseEntity<BrokerAccountDto.SyncResponse> requestSync(
            @PathVariable Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.SyncResponse response = assetSyncService.requestSync(userId, accountId);
        // 비동기 처리의 경우 accepted(202)가 맞으나, 현재 동기 처리 방식이므로 ok(200) 반환
        return ResponseEntity.ok(response);
    }

    /**
     * 동기화 상태 조회
     */
    @GetMapping("/{userId}/accounts/{accountId}/sync/{syncId}")
    public ResponseEntity<BrokerAccountDto.SyncStatusResponse> getSyncStatus(
            @PathVariable Long userId,
            @PathVariable Long accountId,
            @PathVariable Long syncId) {
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
        BrokerAccountDto.CombinedPortfolioResponse response = portfolioAggregationService
                .getUserCombinedPortfolio(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 특정 계좌 포트폴리오 조회
     */
    @GetMapping("/{userId}/accounts/{accountId}/portfolio")
    public ResponseEntity<BrokerAccountDto.AccountPortfolioDto> getAccountPortfolio(
            @PathVariable Long userId,
            @PathVariable Long accountId) {
        BrokerAccountDto.AccountPortfolioDto response = portfolioAggregationService.getAccountPortfolio(userId,
                accountId);
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
