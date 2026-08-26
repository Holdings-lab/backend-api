package com.project.server.service.integration.kis;

import java.math.BigDecimal;
import java.util.List;

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

    record KisPosition(
            String itemCode,
            String itemName,
            String productType,
            String productCode,
            BigDecimal quantity,
            BigDecimal purchaseUnitPrice,
            BigDecimal presentPrice,
            BigDecimal valuationAmount,
            BigDecimal purchaseAmount,
            BigDecimal valuationGainLoss,
            BigDecimal profitRate,
            String currencyCode,
            String overseasYn) {
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
            List<KisPosition> positions) {
    }

    KisBalanceSnapshot fetchBalance(KisCredential credential);
}
