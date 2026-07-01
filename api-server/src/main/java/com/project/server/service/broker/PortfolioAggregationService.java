package com.project.server.service.broker;

import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.AccountBalanceRepository;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioAggregationService {

    private final BrokerAccountRepository brokerAccountRepository;
    private final AssetPositionRepository assetPositionRepository;
    private final AccountBalanceRepository accountBalanceRepository;

    public BrokerAccountDto.CombinedPortfolioResponse getUserCombinedPortfolio(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }

        List<BrokerAccountEntity> accounts = brokerAccountRepository.findByUserId(userId);

        if (accounts.isEmpty()) {
            return BrokerAccountDto.CombinedPortfolioResponse.builder()
                    .estimatedDepositAsset(BigDecimal.ZERO)
                    .cashBalance(BigDecimal.ZERO)
                    .totalPurchaseAmount(BigDecimal.ZERO)
                    .totalValuationAmount(BigDecimal.ZERO)
                    .totalValuationGainLoss(BigDecimal.ZERO)
                    .totalProfitRate(BigDecimal.ZERO)
                    .positions(List.of())
                    .byBroker(Map.of())
                    .lastSyncedAt(null)
                    .build();
        }

        BigDecimal estimatedDepositAsset = BigDecimal.ZERO;
        BigDecimal totalCashBalance = BigDecimal.ZERO;
        BigDecimal totalPurchaseAmount = BigDecimal.ZERO;
        BigDecimal totalValuationAmount = BigDecimal.ZERO;
        BigDecimal totalValuationGainLoss = BigDecimal.ZERO;

        Map<String, BrokerAccountDto.AccountPortfolioDto> byBroker = new HashMap<>();
        LocalDateTime latestSyncTime = null;

        for (BrokerAccountEntity account : accounts) {
            AccountBalanceEntity latestBalance = accountBalanceRepository
                    .findTopByAccountIdOrderByAsOfDateDesc(account.getId())
                    .orElse(null);

            if (latestBalance != null) {
                estimatedDepositAsset = estimatedDepositAsset.add(
                        nullToZero(latestBalance.getTotalAssetValue()));
                totalCashBalance = totalCashBalance.add(nullToZero(latestBalance.getCashBalance()));
                totalPurchaseAmount = totalPurchaseAmount.add(nullToZero(latestBalance.getDepositAmount()));
                totalValuationAmount = totalValuationAmount.add(nullToZero(latestBalance.getEvaluationAmount()));
                totalValuationGainLoss = totalValuationGainLoss.add(nullToZero(latestBalance.getGainLoss()));

                if (latestSyncTime == null || latestBalance.getLastSyncedAt().isAfter(latestSyncTime)) {
                    latestSyncTime = latestBalance.getLastSyncedAt();
                }
            }

            List<BrokerAccountDto.AssetPositionDto> positionDtos = assetPositionRepository
                    .findByAccountId(account.getId()).stream()
                    .map(HyphenFieldMapper::toPositionDto)
                    .collect(Collectors.toList());

            byBroker.put(account.getBrokerName() + "_" + account.getAccountNumber(),
                    BrokerAccountDto.AccountPortfolioDto.builder()
                            .accountId(account.getId())
                            .accountNumber(account.getAccountNumber())
                            .brokerName(account.getBrokerName())
                            .estimatedDepositAsset(
                                    latestBalance != null ? nullToZero(latestBalance.getTotalAssetValue()) : BigDecimal.ZERO)
                            .cashBalance(latestBalance != null ? nullToZero(latestBalance.getCashBalance()) : BigDecimal.ZERO)
                            .positions(positionDtos)
                            .lastSyncedAt(latestBalance != null ? latestBalance.getLastSyncedAt() : null)
                            .build());
        }

        BigDecimal totalProfitRate = BigDecimal.ZERO;
        if (totalPurchaseAmount.compareTo(BigDecimal.ZERO) > 0) {
            totalProfitRate = totalValuationGainLoss
                    .divide(totalPurchaseAmount, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100));
        }

        List<BrokerAccountDto.AssetPositionDto> allPositions = accounts.stream()
                .flatMap(account -> assetPositionRepository.findByAccountId(account.getId()).stream())
                .map(HyphenFieldMapper::toPositionDto)
                .collect(Collectors.toList());

        return BrokerAccountDto.CombinedPortfolioResponse.builder()
                .estimatedDepositAsset(estimatedDepositAsset)
                .cashBalance(totalCashBalance)
                .totalPurchaseAmount(totalPurchaseAmount)
                .totalValuationAmount(totalValuationAmount)
                .totalValuationGainLoss(totalValuationGainLoss)
                .totalProfitRate(totalProfitRate)
                .positions(allPositions)
                .byBroker(byBroker)
                .lastSyncedAt(latestSyncTime)
                .build();
    }

    public BrokerAccountDto.AccountPortfolioDto getAccountPortfolio(Long userId, Long accountId) {
        BrokerAccountEntity account = validateAccountAccess(userId, accountId);

        AccountBalanceEntity latestBalance = accountBalanceRepository
                .findTopByAccountIdOrderByAsOfDateDesc(accountId)
                .orElse(null);

        List<BrokerAccountDto.AssetPositionDto> positionDtos = assetPositionRepository.findByAccountId(accountId).stream()
                .map(HyphenFieldMapper::toPositionDto)
                .collect(Collectors.toList());

        return BrokerAccountDto.AccountPortfolioDto.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .brokerName(account.getBrokerName())
                .estimatedDepositAsset(latestBalance != null ? nullToZero(latestBalance.getTotalAssetValue()) : BigDecimal.ZERO)
                .cashBalance(latestBalance != null ? nullToZero(latestBalance.getCashBalance()) : BigDecimal.ZERO)
                .positions(positionDtos)
                .lastSyncedAt(latestBalance != null ? latestBalance.getLastSyncedAt() : null)
                .build();
    }

    public Map<String, Object> analyzeAssetAllocation(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }

        List<BrokerAccountEntity> accounts = brokerAccountRepository.findByUserId(userId);

        Map<String, BigDecimal> assetTypeDistribution = new HashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        for (BrokerAccountEntity account : accounts) {
            for (var position : assetPositionRepository.findByAccountId(account.getId())) {
                String productType = position.getPositionType() != null ? position.getPositionType() : "UNKNOWN";
                BigDecimal valuationAmount = nullToZero(position.getCurrentValue());
                assetTypeDistribution.merge(productType, valuationAmount, BigDecimal::add);
                totalValue = totalValue.add(valuationAmount);
            }
        }

        Map<String, Object> allocation = new HashMap<>();
        BigDecimal finalTotalValue = totalValue;

        assetTypeDistribution.forEach((type, value) -> {
            BigDecimal percentage = BigDecimal.ZERO;
            
            // 총합이 0보다 클 때만 퍼센트 계산 (0으로 나누는 에러 방지)
            if (finalTotalValue.compareTo(BigDecimal.ZERO) > 0) {
                percentage = value.divide(finalTotalValue, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100));
            }
            allocation.put(type, Map.of("value", value, "percentage", percentage));
        });
        allocation.put("totalValue", totalValue);
        return allocation;
    }

    /**
     * 포트폴리오 성과 분석
     */
    public Map<String, Object> analyzePerformance(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }

        BrokerAccountDto.CombinedPortfolioResponse portfolio = getUserCombinedPortfolio(userId);

        return Map.of(
                "totalPurchaseAmount", portfolio.getTotalPurchaseAmount(),
                "estimatedDepositAsset", portfolio.getEstimatedDepositAsset(),
                "totalValuationGainLoss", portfolio.getTotalValuationGainLoss(),
                "totalProfitRate", portfolio.getTotalProfitRate(),
                "positions", portfolio.getPositions().size(),
                "lastSyncedAt", portfolio.getLastSyncedAt() != null ? portfolio.getLastSyncedAt() : ""
        );
    }

    /**
     * 계좌 유효성 검증 및 조회 공통 로직
     */
    private BrokerAccountEntity validateAccountAccess(Long userId, Long accountId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
        if (accountId == null || accountId <= 0) {
            throw ApiException.badRequest("유효하지 않은 계좌 ID입니다.", "INVALID_ACCOUNT_ID");
        }

        BrokerAccountEntity account = brokerAccountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("계좌를 찾을 수 없습니다.", "ACCOUNT_NOT_FOUND"));

        if (!account.getUserId().equals(userId)) {
            throw ApiException.badRequest("해당 계좌의 소유자와 요청한 사용자가 일치하지 않습니다.", "USER_MISMATCH");
        }

        return account;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
