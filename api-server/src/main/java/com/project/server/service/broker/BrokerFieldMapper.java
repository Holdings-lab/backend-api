package com.project.server.service.broker;

import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.dto.BrokerAccountDto;

import java.math.BigDecimal;
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
                .fxRates(balance.getFxRates() != null ? balance.getFxRates() : java.util.Map.of())
                .estimatedDepositAsset(balance.getTotalAssetValue())
                .cashBalance(balance.getCashBalance())
                .totalPurchaseAmount(balance.getDepositAmount())
                .totalValuationAmount(balance.getEvaluationAmount())
                .totalValuationGainLoss(balance.getGainLoss())
                .totalProfitRate(balance.getGainLossRate())
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
                .productCode(entity.getProductCode())
                .quantity(entity.getQuantity())
                .profitRate(entity.getGainLossRate())
                .currencyCode(entity.getCurrencyCode())
                .overseasYn(entity.getOverseasYn())
                .fxRate(entity.getFxRate())
                .nativeAmounts(BrokerAccountDto.PositionNativeDto.builder()
                        .purchaseUnitPrice(entity.getPurchasePrice())
                        .presentPrice(entity.getCurrentPrice())
                        .purchaseAmount(entity.getNativePurchaseAmount())
                        .valuationAmount(entity.getNativeValuationAmount())
                        .gainLoss(entity.getNativeGainLoss())
                        .build())
                .krw(BrokerAccountDto.PositionKrwDto.builder()
                        .purchaseAmount(entity.getPurchaseAmount())
                        .valuationAmount(entity.getCurrentValue())
                        .gainLoss(entity.getGainLoss())
                        .build())
                .build();
    }

    public static Map<String, BigDecimal> mergeFxRates(Map<String, BigDecimal> left, Map<String, BigDecimal> right) {
        Map<String, BigDecimal> merged = new java.util.LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            right.forEach(merged::putIfAbsent);
        }
        return merged;
    }
}
