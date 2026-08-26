package com.project.server.service.asset;

import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.asset.AssetSnapshotType;
import com.project.server.domain.asset.Status;
import com.project.server.domain.asset.InvestmentHorizon;
import com.project.server.domain.asset.UserAssetAthEntity;
import com.project.server.domain.asset.UserAssetSnapshotEntity;
import com.project.server.domain.asset.UserInvestmentProfileEntity;
import com.project.server.config.AssetProperties;
import com.project.server.exception.ApiException;
import com.project.server.repository.AccountBalanceRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.repository.asset.UserAssetAthRepository;
import com.project.server.repository.asset.UserAssetSnapshotRepository;
import com.project.server.repository.asset.UserInvestmentProfileRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetMetricsService {

    private final BrokerAccountRepository brokerAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final UserAssetAthRepository userAssetAthRepository;
    private final UserAssetSnapshotRepository userAssetSnapshotRepository;
    private final UserInvestmentProfileRepository userInvestmentProfileRepository;
    private final AssetProperties assetProperties;

    public AssetMetrics compute(Long userId) {
        validateUserId(userId);

        BigDecimal assetTotal = calculateAssetTotal(userId);
        BigDecimal dailyChangePct = calculateDailyChangePct(userId, assetTotal);
        BigDecimal drawdownPct = calculateDrawdownPct(userId, assetTotal);

        UserInvestmentProfileEntity profile = getOrDefaultProfile(userId);
        int maxDrawdownTolerance = profile.getMaxDrawdownTolerance() != null
                ? profile.getMaxDrawdownTolerance()
                : 10;
        InvestmentHorizon horizon = profile.getInvestmentHorizon() != null
                ? profile.getInvestmentHorizon()
                : InvestmentHorizon.Y1_3;
        BigDecimal ratio = calculateRatio(drawdownPct, maxDrawdownTolerance);
        Status status = resolveStatus(ratio);

        return AssetMetrics.builder()
                .assetTotal(assetTotal)
                .dailyChangePct(dailyChangePct)
                .drawdownPct(drawdownPct)
                .maxDrawdownTolerance(maxDrawdownTolerance)
                .ratio(ratio)
                .status(status)
                .investmentHorizon(horizon)
                .build();
    }

    public BigDecimal getAssetTotal(Long userId) {
        validateUserId(userId);
        return calculateAssetTotal(userId);
    }

    public boolean hasConnectedAccounts(Long userId) {
        return !getConnectedAccounts(userId).isEmpty();
    }

    public BigDecimal calculateAssetTotal(Long userId) {
        BigDecimal total = BigDecimal.ZERO;
        for (BrokerAccountEntity account : getConnectedAccounts(userId)) {
            AccountBalanceEntity balance = accountBalanceRepository
                    .findTopByAccountIdOrderByAsOfDateDesc(account.getId())
                    .orElse(null);
            if (balance != null && balance.getTotalAssetValue() != null) {
                total = total.add(balance.getTotalAssetValue());
            }
        }
        return total;
    }

    private BigDecimal calculateDailyChangePct(Long userId, BigDecimal currentTotal) {
        return userAssetSnapshotRepository
                .findTopByUserIdAndSnapshotTypeOrderBySnapshotDateDesc(userId, AssetSnapshotType.PREVIOUS_DAY)
                .map(UserAssetSnapshotEntity::getAssetTotal)
                .filter(previous -> previous.compareTo(BigDecimal.ZERO) > 0)
                .map(previous -> currentTotal.subtract(previous)
                        .divide(previous, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP));
    }

    private BigDecimal calculateDrawdownPct(Long userId, BigDecimal currentTotal) {
        UserAssetAthEntity ath = userAssetAthRepository.findById(userId).orElse(null);
        if (ath == null || ath.getAllTimeHighAmount() == null
                || ath.getAllTimeHighAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(0, RoundingMode.HALF_UP);
        }

        return currentTotal.subtract(ath.getAllTimeHighAmount())
                .divide(ath.getAllTimeHighAmount(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRatio(BigDecimal drawdownPct, int maxDrawdownTolerance) {
        if (maxDrawdownTolerance <= 0) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        return drawdownPct.abs()
                .divide(BigDecimal.valueOf(maxDrawdownTolerance), 6, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.HALF_UP);
    }

    private Status resolveStatus(BigDecimal ratio) {
        if (ratio.compareTo(assetProperties.getStatus().getAlertRatio()) >= 0) {
            return Status.ALERT;
        }
        if (ratio.compareTo(assetProperties.getStatus().getWatchRatio()) >= 0) {
            return Status.WATCH;
        }
        return Status.NORMAL;
    }

    private UserInvestmentProfileEntity getOrDefaultProfile(Long userId) {
        return userInvestmentProfileRepository.findById(userId)
                .orElse(UserInvestmentProfileEntity.builder()
                        .userId(userId)
                        .investmentHorizon(InvestmentHorizon.Y1_3)
                        .maxDrawdownTolerance(10)
                        .build());
    }

    private List<BrokerAccountEntity> getConnectedAccounts(Long userId) {
        return brokerAccountRepository.findByUserId(userId).stream()
                .filter(account -> account.getConnectionStatus() == BrokerAccountEntity.ConnectionStatus.CONNECTED)
                .toList();
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
    }

    @Builder
    public record AssetMetrics(
            BigDecimal assetTotal,
            BigDecimal dailyChangePct,
            BigDecimal drawdownPct,
            int maxDrawdownTolerance,
            BigDecimal ratio,
            Status status,
            InvestmentHorizon investmentHorizon) {
    }
}
