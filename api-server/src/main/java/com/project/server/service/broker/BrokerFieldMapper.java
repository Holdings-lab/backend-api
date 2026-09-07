package com.project.server.service.broker;

import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.service.integration.kis.KisApiClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    public static BrokerAccountDto.AccountBalanceDto toBalanceDto(
            KisApiClient.KisBalanceSnapshot snapshot,
            LocalDateTime syncedAt) {
        if (snapshot == null) {
            return null;
        }
        BigDecimal cash = nullToZero(snapshot.cashBalance());
        BigDecimal evaluation = nullToZero(snapshot.evaluationAmount());
        BigDecimal total = nullToZero(snapshot.totalAssetValue());
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            total = evaluation.add(cash);
        }
        return BrokerAccountDto.AccountBalanceDto.builder()
                .currencyCode("KRW")
                .fxRates(scaleFxRates(snapshot.fxRates()))
                .estimatedDepositAsset(krw(total))
                .cashBalance(krw(cash))
                .totalPurchaseAmount(krw(snapshot.purchaseAmount()))
                .totalValuationAmount(krw(evaluation))
                .totalValuationGainLoss(krw(snapshot.gainLoss()))
                .totalProfitRate(rate(snapshot.gainLossRate()))
                .asOfDate(LocalDate.now())
                .lastSyncedAt(syncedAt)
                .build();
    }

    public static List<BrokerAccountDto.AssetPositionDto> toPositionDtos(KisApiClient.KisBalanceSnapshot snapshot) {
        if (snapshot == null || snapshot.positions() == null) {
            return List.of();
        }
        List<BrokerAccountDto.AssetPositionDto> positions = new ArrayList<>();
        for (KisApiClient.KisPosition position : snapshot.positions()) {
            if (position.itemCode() == null || position.itemCode().isBlank()) {
                continue;
            }
            if (!"Y".equalsIgnoreCase(position.overseasYn())) {
                continue;
            }
            positions.add(toPositionDto(position));
        }
        return positions;
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
                .fxRate(isZero(entity.getFxRate()) ? null : fx(entity.getFxRate()))
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

    public static BrokerAccountDto.AssetPositionDto toPositionDto(KisApiClient.KisPosition position) {
        KisApiClient.NativeQuote nativeQuote = position.nativeQuote();
        KisApiClient.KrwQuote krwQuote = position.krw();
        return BrokerAccountDto.AssetPositionDto.builder()
                .itemCode(position.itemCode())
                .itemName(position.itemName())
                .productType(position.productType())
                .quantity(qty(position.quantity()))
                .profitRate(rate(position.profitRate()))
                .currencyCode(position.currencyCode())
                .fxRate(isZero(position.fxRate()) ? null : fx(position.fxRate()))
                .nativeAmounts(BrokerAccountDto.PositionNativeDto.builder()
                        .purchaseUnitPrice(nativeQuote == null ? null : nativeUnit(nativeQuote.purchaseUnitPrice()))
                        .presentPrice(nativeQuote == null ? null : nativeUnit(nativeQuote.presentPrice()))
                        .purchaseAmount(nativeQuote == null ? null : nativeAmount(nativeQuote.purchaseAmount()))
                        .valuationAmount(nativeQuote == null ? null : nativeAmount(nativeQuote.valuationAmount()))
                        .gainLoss(nativeQuote == null ? null : nativeAmount(nativeQuote.gainLoss()))
                        .build())
                .krw(BrokerAccountDto.PositionKrwDto.builder()
                        .purchaseAmount(krwQuote == null ? null : krw(krwQuote.purchaseAmount()))
                        .valuationAmount(krwQuote == null ? null : krw(krwQuote.valuationAmount()))
                        .gainLoss(krwQuote == null ? null : krw(krwQuote.gainLoss()))
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
            if (currency != null && !currency.isBlank() && rate != null && rate.compareTo(BigDecimal.ZERO) > 0) {
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

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }
}
