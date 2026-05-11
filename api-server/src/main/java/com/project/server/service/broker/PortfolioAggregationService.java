package com.project.server.service.broker;

import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
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

    /**
     * 사용자의 모든 계좌 통합 포트폴리오 조회
     */
    public BrokerAccountDto.CombinedPortfolioResponse getUserCombinedPortfolio(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }

        List<BrokerAccountEntity> accounts = brokerAccountRepository.findByUserId(userId);

        if (accounts.isEmpty()) {
            // 연동된 계좌가 없으면 빈 포트폴리오 반환
            return BrokerAccountDto.CombinedPortfolioResponse.builder()
                    .totalAssetValue(BigDecimal.ZERO)
                    .cashBalance(BigDecimal.ZERO)
                    .depositAmount(BigDecimal.ZERO)
                    .evaluationAmount(BigDecimal.ZERO)
                    .gainLoss(BigDecimal.ZERO)
                    .gainLossRate(BigDecimal.ZERO)
                    .dailyGainLoss(BigDecimal.ZERO)
                    .dailyGainLossRate(BigDecimal.ZERO)
                    .positions(List.of())
                    .byBroker(Map.of())
                    .lastSyncedAt(null)
                    .build();
        }

        // 각 계좌별 포트폴리오 계산
        BigDecimal totalAssetValue = BigDecimal.ZERO;
        BigDecimal totalCashBalance = BigDecimal.ZERO;
        BigDecimal totalDepositAmount = BigDecimal.ZERO;
        BigDecimal totalEvaluationAmount = BigDecimal.ZERO;
        BigDecimal totalGainLoss = BigDecimal.ZERO;
        BigDecimal totalDailyGainLoss = BigDecimal.ZERO;

        Map<String, BrokerAccountDto.AccountPortfolioDto> byBroker = new HashMap<>();
        List<BrokerAccountDto.AssetPositionDto> allPositions = List.of();
        LocalDateTime latestSyncTime = null;

        for (BrokerAccountEntity account : accounts) {
            // 최신 잔액 정보 조회
            AccountBalanceEntity latestBalance = accountBalanceRepository
                    .findTopByAccountIdOrderByAsOfDateDesc(account.getId())
                    .orElse(null);

            if (latestBalance != null) {
                totalAssetValue = totalAssetValue.add(latestBalance.getTotalAssetValue() != null ? latestBalance.getTotalAssetValue() : BigDecimal.ZERO);
                totalCashBalance = totalCashBalance.add(latestBalance.getCashBalance() != null ? latestBalance.getCashBalance() : BigDecimal.ZERO);
                totalDepositAmount = totalDepositAmount.add(latestBalance.getDepositAmount() != null ? latestBalance.getDepositAmount() : BigDecimal.ZERO);
                totalEvaluationAmount = totalEvaluationAmount.add(latestBalance.getEvaluationAmount() != null ? latestBalance.getEvaluationAmount() : BigDecimal.ZERO);
                totalGainLoss = totalGainLoss.add(latestBalance.getGainLoss() != null ? latestBalance.getGainLoss() : BigDecimal.ZERO);
                totalDailyGainLoss = totalDailyGainLoss.add(latestBalance.getDailyGainLoss() != null ? latestBalance.getDailyGainLoss() : BigDecimal.ZERO);

                if (latestSyncTime == null || latestBalance.getLastSyncedAt().isAfter(latestSyncTime)) {
                    latestSyncTime = latestBalance.getLastSyncedAt();
                }
            }

            // 계좌별 포지션 조회
            List<AssetPositionEntity> positions = assetPositionRepository.findByAccountId(account.getId());
            List<BrokerAccountDto.AssetPositionDto> positionDtos = positions.stream()
                    .map(this::toPositionDto)
                    .collect(Collectors.toList());

            BrokerAccountDto.AccountPortfolioDto accountPortfolio = BrokerAccountDto.AccountPortfolioDto.builder()
                    .accountId(account.getId())
                    .accountNumber(account.getAccountNumber())
                    .brokerName(account.getBrokerName())
                    .totalAssetValue(latestBalance != null ? latestBalance.getTotalAssetValue() : BigDecimal.ZERO)
                    .cashBalance(latestBalance != null ? latestBalance.getCashBalance() : BigDecimal.ZERO)
                    .positions(positionDtos)
                    .lastSyncedAt(latestBalance != null ? latestBalance.getLastSyncedAt() : null)
                    .build();

            byBroker.put(account.getBrokerName() + "_" + account.getAccountNumber(), accountPortfolio);
        }

        // 전체 수익률 계산
        BigDecimal gainLossRate = BigDecimal.ZERO;
        if (totalDepositAmount.compareTo(BigDecimal.ZERO) > 0) {
            gainLossRate = totalGainLoss.divide(totalDepositAmount, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100));
        }

        BigDecimal dailyGainLossRate = BigDecimal.ZERO;
        if (totalAssetValue.compareTo(BigDecimal.ZERO) > 0) {
            dailyGainLossRate = totalDailyGainLoss.divide(totalAssetValue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(new BigDecimal(100));
        }

        // 모든 포지션 수집
        allPositions = accounts.stream()
                .flatMap(account -> assetPositionRepository.findByAccountId(account.getId()).stream())
                .map(this::toPositionDto)
                .collect(Collectors.toList());

        return BrokerAccountDto.CombinedPortfolioResponse.builder()
                .totalAssetValue(totalAssetValue)
                .cashBalance(totalCashBalance)
                .depositAmount(totalDepositAmount)
                .evaluationAmount(totalEvaluationAmount)
                .gainLoss(totalGainLoss)
                .gainLossRate(gainLossRate)
                .dailyGainLoss(totalDailyGainLoss)
                .dailyGainLossRate(dailyGainLossRate)
                .positions(allPositions)
                .byBroker(byBroker)
                .lastSyncedAt(latestSyncTime)
                .build();
    }

    /**
     * 특정 계좌의 포트폴리오 조회
     */
    public BrokerAccountDto.AccountPortfolioDto getAccountPortfolio(Long userId, Long accountId) {
        BrokerAccountEntity account = validateAccountAccess(userId, accountId);

        AccountBalanceEntity latestBalance = accountBalanceRepository
                .findTopByAccountIdOrderByAsOfDateDesc(accountId)
                .orElse(null);

        List<AssetPositionEntity> positions = assetPositionRepository.findByAccountId(accountId);
        List<BrokerAccountDto.AssetPositionDto> positionDtos = positions.stream()
                .map(this::toPositionDto)
                .collect(Collectors.toList());

        return BrokerAccountDto.AccountPortfolioDto.builder()
                .accountId(accountId)
                .accountNumber(account.getAccountNumber())
                .brokerName(account.getBrokerName())
                .totalAssetValue(latestBalance != null ? latestBalance.getTotalAssetValue() : BigDecimal.ZERO)
                .cashBalance(latestBalance != null ? latestBalance.getCashBalance() : BigDecimal.ZERO)
                .positions(positionDtos)
                .lastSyncedAt(latestBalance != null ? latestBalance.getLastSyncedAt() : null)
                .build();
    }

    /**
     * AssetPositionEntity를 DTO로 변환
     */
    private BrokerAccountDto.AssetPositionDto toPositionDto(AssetPositionEntity entity) {
        return BrokerAccountDto.AssetPositionDto.builder()
                .symbol(entity.getSymbol())
                .positionType(entity.getPositionType())
                .quantity(entity.getQuantity())
                .purchasePrice(entity.getPurchasePrice())
                .currentPrice(entity.getCurrentPrice())
                .currentValue(entity.getCurrentValue())
                .purchaseAmount(entity.getPurchaseAmount())
                .gainLoss(entity.getGainLoss())
                .gainLossRate(entity.getGainLossRate())
                .currencyCode(entity.getCurrencyCode())
                .purchasedAt(entity.getPurchasedAt())
                .build();
    }

    /**
     * 포트폴리오 분석: 자산 배분
     */
    public Map<String, Object> analyzeAssetAllocation(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }

        List<BrokerAccountEntity> accounts = brokerAccountRepository.findByUserId(userId);

        Map<String, BigDecimal> assetTypeDistribution = new HashMap<>();
        BigDecimal totalValue = BigDecimal.ZERO;

        for (BrokerAccountEntity account : accounts) {
            List<AssetPositionEntity> positions = assetPositionRepository.findByAccountId(account.getId());

            for (AssetPositionEntity position : positions) {
                String positionType = position.getPositionType() != null ? position.getPositionType() : "UNKNOWN";
                BigDecimal currentValue = position.getCurrentValue() != null ? position.getCurrentValue() : BigDecimal.ZERO;

                assetTypeDistribution.merge(positionType, currentValue, BigDecimal::add);
                totalValue = totalValue.add(currentValue);
            }
        }

        // 비율 계산
        Map<String, Object> allocation = new HashMap<>();
        BigDecimal finalTotalValue = totalValue;
        
        assetTypeDistribution.forEach((type, value) -> {
            BigDecimal percentage = BigDecimal.ZERO;
            
            // 총합이 0보다 클 때만 퍼센트 계산 (0으로 나누는 에러 방지)
            if (finalTotalValue.compareTo(BigDecimal.ZERO) > 0) {
                percentage = value.divide(finalTotalValue, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal(100));
            }
            
            // 조건문 밖에서 무조건 맵에 삽입!
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
                "totalInvestment", portfolio.getDepositAmount(),
                "currentValue", portfolio.getTotalAssetValue(),
                "totalGain", portfolio.getGainLoss(),
                "gainRate", portfolio.getGainLossRate(),
                "dailyGain", portfolio.getDailyGainLoss(),
                "dailyGainRate", portfolio.getDailyGainLossRate(),
                "positions", portfolio.getPositions().size(),
                "lastSyncedAt", portfolio.getLastSyncedAt()
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
}
