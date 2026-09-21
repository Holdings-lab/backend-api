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

    /**
     * @param exchangeCode 잔고 API 거래소 코드 (NASD/NYSE/AMEX 등). 시세 EXCD 매핑에 사용.
     * @param dailyChangePct 당일 등락률(%). 해외 현재가 API {@code rate}. 없으면 null.
     */
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
            KrwQuote krw,
            String exchangeCode,
            BigDecimal dailyChangePct) {
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

    record OverseasPriceQuote(
            String exchangeCode,
            String symbol,
            BigDecimal last,
            BigDecimal base,
            BigDecimal rate) {
    }

    KisBalanceSnapshot fetchBalance(KisCredential credential);

    KisBalanceSnapshot fetchBalance(KisCredential credential, boolean allowExchangeFallback);

    /**
     * 해외주식 현재가. 실패 시 null (호출측에서 티커 단위로 스킵).
     *
     * @param priceExcd 시세용 EXCD (NAS/NYS/AMS 등)
     * @param symbol    티커
     */
    OverseasPriceQuote fetchOverseasPrice(KisCredential credential, String priceExcd, String symbol);
}
