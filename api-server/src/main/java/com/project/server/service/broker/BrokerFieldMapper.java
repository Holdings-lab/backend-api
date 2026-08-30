package com.project.server.service.broker;

import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.dto.BrokerAccountDto;

public final class BrokerFieldMapper {

    private BrokerFieldMapper() {
    }

    public static BrokerAccountDto.AccountBalanceDto toBalanceDto(AccountBalanceEntity balance) {
        if (balance == null) {
            return null;
        }
        return BrokerAccountDto.AccountBalanceDto.builder()
                .id(balance.getId())
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
                .purchaseUnitPrice(entity.getPurchasePrice())
                .presentPrice(entity.getCurrentPrice())
                .valuationAmount(entity.getCurrentValue())
                .purchaseAmount(entity.getPurchaseAmount())
                .valuationGainLoss(entity.getGainLoss())
                .profitRate(entity.getGainLossRate())
                .currencyCode(entity.getCurrencyCode())
                .overseasYn(entity.getOverseasYn())
                .build();
    }
}
