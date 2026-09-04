package com.project.server.service.integration.kis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface KisApiClient {

    record KisCredential(
            String appKey,
            String appSecret,
            String cano,
            String accountProductCode,
            BrokerAccountCredentialSource source) {
    }

    enum BrokerAccountCredentialSource {
        ENV,
        USER
    }

    record NativeQuote(
            BigDecimal purchaseUnitPrice,
            BigDecimal presentPrice,
            BigDecimal purchaseAmount,
            BigDecimal valuationAmount,
            BigDecimal gainLoss) {
    }

    record KrwQuote(
            BigDecimal purchaseAmount,
            BigDecimal valuationAmount,
            BigDecimal gainLoss) {
    }

    record KisPosition(
            String itemCode,
            String itemName,
            String productType,
            String productCode,
            BigDecimal quantity,
            BigDecimal profitRate,
            String currencyCode,
            String overseasYn,
            BigDecimal fxRate,
            NativeQuote nativeQuote,
            KrwQuote krw) {
    }

    record KisBalanceSnapshot(
            String cano,
            String accountProductCode,
            String accountDisplay,
            BigDecimal cashBalance,
            BigDecimal totalAssetValue,
            BigDecimal evaluationAmount,
            BigDecimal purchaseAmount,
            BigDecimal gainLoss,
            BigDecimal gainLossRate,
            Map<String, BigDecimal> fxRates,
            List<KisPosition> positions) {
    }

    KisBalanceSnapshot fetchBalance(KisCredential credential);
}
