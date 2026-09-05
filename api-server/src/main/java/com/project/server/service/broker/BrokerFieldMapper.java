package com.project.server.service.broker;

import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.dto.BrokerAccountDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BrokerFieldMapper {

    private BrokerFieldMapper() {
    }

    public static BrokerAccountDto.AccountBalanceDto toBalanceDto(AccountBalanceEntity balance) {
        if (balance == null) {
            return null;
        }
        return BrokerAccountDto.AccountBalanceDto.builder()
                .id(balance.getId())
                .currencyCode(balance.getCurrencyCode() != null ? balance.getCurrencyCode() : "KRW")
                .fxRates(scaleFxRates(balance.getFxRates()))
                .estimatedDepositAsset(krw(balance.getTotalAssetValue()))
                .cashBalance(krw(balance.getCashBalance()))
                .totalPurchaseAmount(krw(balance.getDepositAmount()))
                .totalValuationAmount(krw(balance.getEvaluationAmount()))
                .totalValuationGainLoss(krw(balance.getGainLoss()))
                .totalProfitRate(rate(balance.getGainLossRate()))
                .asOfDate(balance.getAsOfDate())
                .lastSyncedAt(balance.getLastSyncedAt())
                .build();
    }

    public static boolean isOverseas(AssetPositionEntity entity) {
        return entity != null && "Y".equalsIgnoreCase(entity.getOverseasYn());
    }

    public static BrokerAccountDto.AssetPositionDto toPositionDto(AssetPositionEntity entity) {
        return BrokerAccountDto.AssetPositionDto.builder()
                .itemCode(entity.getItemCode() != null ? entity.getItemCode() : entity.getSymbol())
                .itemName(entity.getItemName())
                .productType(entity.getPositionType())
                .quantity(qty(entity.getQuantity()))
                .profitRate(rate(entity.getGainLossRate()))
                .currencyCode(entity.getCurrencyCode())
                .fxRate(fx(entity.getFxRate()))
                .nativeAmounts(BrokerAccountDto.PositionNativeDto.builder()
                        .purchaseUnitPrice(nativeUnit(entity.getPurchasePrice()))
                        .presentPrice(nativeUnit(entity.getCurrentPrice()))
                        .purchaseAmount(nativeAmount(entity.getNativePurchaseAmount()))
                        .valuationAmount(nativeAmount(entity.getNativeValuationAmount()))
                        .gainLoss(nativeAmount(entity.getNativeGainLoss()))
                        .build())
                .krw(BrokerAccountDto.PositionKrwDto.builder()
                        .purchaseAmount(krw(entity.getPurchaseAmount()))
                        .valuationAmount(krw(entity.getCurrentValue()))
                        .gainLoss(krw(entity.getGainLoss()))
                        .build())
                .build();
    }

    public static Map<String, BigDecimal> mergeFxRates(Map<String, BigDecimal> left, Map<String, BigDecimal> right) {
        Map<String, BigDecimal> merged = new LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            right.forEach(merged::putIfAbsent);
        }
        return scaleFxRates(merged);
    }

    public static Map<String, BigDecimal> scaleFxRates(Map<String, BigDecimal> fxRates) {
        if (fxRates == null || fxRates.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> scaled = new LinkedHashMap<>();
        fxRates.forEach((currency, rate) -> {
            if (currency != null && !currency.isBlank() && rate != null) {
                scaled.put(currency, fx(rate));
            }
        });
        return scaled;
    }

    private static BigDecimal krw(BigDecimal value) {
        return scale(value, 2);
    }

    private static BigDecimal nativeUnit(BigDecimal value) {
        return scale(value, 4);
    }

    private static BigDecimal nativeAmount(BigDecimal value) {
        return scale(value, 4);
    }

    private static BigDecimal qty(BigDecimal value) {
        return scale(value, 2);
    }

    private static BigDecimal rate(BigDecimal value) {
        return scale(value, 2);
    }

    private static BigDecimal fx(BigDecimal value) {
        return scale(value, 4);
    }

    private static BigDecimal scale(BigDecimal value, int places) {
        if (value == null) {
            return null;
        }
        return value.setScale(places, RoundingMode.HALF_UP);
    }
}
