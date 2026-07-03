package com.project.server.service.broker;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.dto.BrokerAccountDto;

import java.util.Map;

/**
 * 하이픈 API 응답 필드 → 공개 API DTO 매핑.
 */
public final class HyphenFieldMapper {

    private HyphenFieldMapper() {
    }

    public static BrokerAccountDto.AccountSnapshot toAccountSnapshot(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        return BrokerAccountDto.AccountSnapshot.builder()
                .accountDisplay(stringValue(details, "acctDisp"))
                .accountName(stringValue(details, "acctNm"))
                .accountNick(stringValue(details, "acctNick"))
                .openDate(stringValue(details, "openDt"))
                .endDate(stringValue(details, "endDt"))
                .lastTradeDate(stringValue(details, "lastDt"))
                .balance(stringValue(details, "balance"))
                .currencyCode(stringValue(details, "curCd"))
                .dormantYn(stringValue(details, "dormantYn"))
                .availableBalance(stringValue(details, "ablBal"))
                .accountHolder(stringValue(details, "acctHolder"))
                .build();
    }

    public static BrokerAccountDto.AccountSnapshot toAccountSnapshot(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return BrokerAccountDto.AccountSnapshot.builder()
                .accountDisplay(text(node, "acctDisp"))
                .accountName(text(node, "acctNm"))
                .accountNick(text(node, "acctNick"))
                .openDate(text(node, "openDt"))
                .endDate(text(node, "endDt"))
                .lastTradeDate(text(node, "lastDt"))
                .balance(text(node, "balance"))
                .currencyCode(text(node, "curCd"))
                .dormantYn(text(node, "dormantYn"))
                .availableBalance(text(node, "ablBal"))
                .accountHolder(text(node, "acctHolder"))
                .build();
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

    private static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static String text(JsonNode node, String key) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
