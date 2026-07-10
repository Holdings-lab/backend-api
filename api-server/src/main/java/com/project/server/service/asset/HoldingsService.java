package com.project.server.service.asset;

import com.project.server.domain.AssetPositionEntity;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.dto.UserAssetDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HoldingsService {

    private static final String ETC_TICKER = "ETC";
    private static final String ETC_NAME = "그 외 보유종목";

    private final BrokerAccountRepository brokerAccountRepository;
    private final AssetPositionRepository assetPositionRepository;
    private final AssetMetricsService assetMetricsService;

    public UserAssetDto.HoldingsResponse getHoldings(Long userId) {
        validateUserId(userId);

        List<BrokerAccountEntity> connectedAccounts = brokerAccountRepository.findByUserId(userId).stream()
                .filter(account -> account.getHyphenStatus() == BrokerAccountEntity.HyphenStatus.CONNECTED)
                .toList();

        if (connectedAccounts.isEmpty()) {
            return UserAssetDto.HoldingsResponse.builder()
                    .holdings(List.of())
                    .build();
        }

        Map<String, AggregatedPosition> aggregated = new LinkedHashMap<>();
        for (BrokerAccountEntity account : connectedAccounts) {
            for (AssetPositionEntity position : assetPositionRepository.findByAccountId(account.getId())) {
                String ticker = resolveTicker(position);
                if (ticker == null || ticker.isBlank()) {
                    continue;
                }
                aggregated.merge(ticker,
                        new AggregatedPosition(ticker, position.getItemName(), nullToZero(position.getCurrentValue())),
                        (left, right) -> new AggregatedPosition(
                                left.ticker(),
                                left.name() != null ? left.name() : right.name(),
                                left.value().add(right.value())));
            }
        }

        BigDecimal assetTotal = assetMetricsService.getAssetTotal(userId);
        if (assetTotal.compareTo(BigDecimal.ZERO) <= 0 || aggregated.isEmpty()) {
            return UserAssetDto.HoldingsResponse.builder()
                    .holdings(List.of())
                    .build();
        }

        List<AggregatedPosition> sorted = aggregated.values().stream()
                .sorted(Comparator.comparing(AggregatedPosition::value).reversed())
                .toList();

        List<UserAssetDto.HoldingItem> holdings = new ArrayList<>();
        BigDecimal topWeightSum = BigDecimal.ZERO;

        int topCount = Math.min(3, sorted.size());
        for (int i = 0; i < topCount; i++) {
            AggregatedPosition position = sorted.get(i);
            BigDecimal weight = toWeightPct(position.value(), assetTotal);
            topWeightSum = topWeightSum.add(weight);
            holdings.add(UserAssetDto.HoldingItem.builder()
                    .ticker(position.ticker())
                    .name(position.name() != null ? position.name() : position.ticker())
                    .weightPct(weight)
                    .build());
        }

        BigDecimal etcWeight = BigDecimal.valueOf(100).subtract(topWeightSum).setScale(1, RoundingMode.HALF_UP);
        if (etcWeight.compareTo(BigDecimal.ZERO) > 0) {
            holdings.add(UserAssetDto.HoldingItem.builder()
                    .ticker(ETC_TICKER)
                    .name(ETC_NAME)
                    .weightPct(etcWeight)
                    .build());
        }

        return UserAssetDto.HoldingsResponse.builder()
                .holdings(holdings)
                .build();
    }

    private BigDecimal toWeightPct(BigDecimal value, BigDecimal total) {
        return value.divide(total, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private String resolveTicker(AssetPositionEntity position) {
        if (position.getSymbol() != null && !position.getSymbol().isBlank()) {
            return position.getSymbol();
        }
        return position.getItemCode();
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
    }

    private record AggregatedPosition(String ticker, String name, BigDecimal value) {
    }
}
