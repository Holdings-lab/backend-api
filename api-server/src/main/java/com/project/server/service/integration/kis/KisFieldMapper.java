package com.project.server.service.integration.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.exception.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KisFieldMapper {

    private KisFieldMapper() {
    }

    public static KisApiClient.KisBalanceSnapshot toOverseasSnapshot(
            KisApiClient.KisCredential credential,
            List<KisApiClient.KisPosition> overseasPositions,
            JsonNode overseasOutput2,
            JsonNode overseasOutput3) {
        JsonNode output3 = firstObject(overseasOutput3);
        BigDecimal cash = firstDecimal(output3, "tot_dncl_amt", "dncl_amt");
        BigDecimal evaluation = firstDecimal(output3, "evlu_amt_smtl", "evlu_amt_smtl_amt", "frcr_evlu_tota");
        BigDecimal purchase = firstDecimal(output3, "pchs_amt_smtl", "pchs_amt_smtl_amt");
        BigDecimal gainLoss = firstDecimal(output3, "evlu_pfls_amt_smtl", "tot_evlu_pfls_amt");
        BigDecimal overseasTotal = evaluation.add(cash);
        BigDecimal reportedTotal = firstDecimal(output3, "tot_asst_amt");
        BigDecimal total = overseasTotal;
        if (!isZero(reportedTotal)
                && reportedTotal.compareTo(overseasTotal.multiply(new BigDecimal("2"))) <= 0) {
            total = reportedTotal;
        }
        BigDecimal gainLossRate = purchase.compareTo(BigDecimal.ZERO) > 0
                ? gainLoss.divide(purchase, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        List<KisApiClient.KisPosition> positions = applyFxAndKrw(
                overseasPositions,
                collectFxFromOutput2(overseasOutput2),
                deriveUsdKrw(overseasPositions, purchase, evaluation));

        return new KisApiClient.KisBalanceSnapshot(
                credential.cano(),
                credential.accountProductCode(),
                KisAccountParser.display(credential.cano(), credential.accountProductCode()),
                cash,
                total,
                evaluation,
                purchase,
                gainLoss,
                gainLossRate,
                collectFxRates(positions),
                positions);
    }

    public static List<KisApiClient.KisPosition> toOverseasPresentPositions(JsonNode output1) {
        List<KisApiClient.KisPosition> positions = new ArrayList<>();
        for (JsonNode row : asRows(output1)) {
            KisApiClient.KisPosition position = toOverseasPosition(row);
            if (position != null) {
                positions.add(position);
            }
        }
        return positions;
    }

    public static List<KisApiClient.KisPosition> toOverseasBalancePositions(JsonNode output1) {
        return toOverseasPresentPositions(output1);
    }

    public static Map<String, Object> toAccountDetails(KisApiClient.KisBalanceSnapshot snapshot) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cano", snapshot.cano());
        details.put("accountProductCode", snapshot.accountProductCode());
        details.put("accountDisplay", snapshot.accountDisplay());
        details.put("accountNick", "한국투자");
        details.put("balance", text(snapshot.totalAssetValue()));
        details.put("availableBalance", text(snapshot.cashBalance()));
        details.put("currencyCode", "KRW");
        return details;
    }

    private static KisApiClient.KisPosition toOverseasPosition(JsonNode row) {
        String itemCode = text(row, "pdno", "ovrs_pdno", "std_pdno");
        if (itemCode == null || itemCode.isBlank()) {
            return null;
        }
        BigDecimal quantity = firstDecimal(row, "cblc_qty13", "ovrs_cblc_qty", "cblc_qty", "hldg_qty");
        if (quantity.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }

        BigDecimal fxRate = firstDecimal(row, "bass_exrt", "frst_bltn_exrt", "exrt", "ovrs_exrt", "wcrc_exrt");
        BigDecimal nativeAvg = firstDecimal(row, "avg_unpr3", "pchs_avg_pric");
        BigDecimal nativePrice = firstDecimal(row, "ovrs_now_pric1", "now_pric2", "prpr");
        BigDecimal nativePurchase = firstDecimal(row, "frcr_pchs_amt", "frcr_pchs_amt1");
        if (isZero(nativePurchase) && !isZero(quantity) && !isZero(nativeAvg)) {
            nativePurchase = quantity.multiply(nativeAvg);
        }
        BigDecimal nativeValuation = !isZero(quantity) && !isZero(nativePrice)
                ? quantity.multiply(nativePrice)
                : BigDecimal.ZERO;
        BigDecimal reportedEval = firstDecimal(row, "frcr_evlu_amt2", "ovrs_stck_evlu_amt", "evlu_amt");
        if (isZero(nativeValuation) && !isZero(reportedEval) && (isZero(fxRate) || !looksLikeKrw(reportedEval, nativeValuation, fxRate))) {
            nativeValuation = reportedEval;
        }
        BigDecimal nativeGain = nativeValuation.subtract(nativePurchase);

        BigDecimal krwPurchase = firstDecimal(row, "pchs_rmnd_wcrc_amt");
        if (isZero(krwPurchase)) {
            krwPurchase = toKrw(nativePurchase, fxRate);
        }
        BigDecimal krwGain = firstDecimal(row, "evlu_pfls_amt2");
        if (isZero(krwGain)) {
            krwGain = firstDecimal(row, "frcr_evlu_pfls_amt", "evlu_pfls_amt");
            if (!isZero(krwGain) && looksLikeKrw(krwGain, nativeGain, fxRate)) {
                // already KRW
            } else if (!isZero(fxRate)) {
                krwGain = toKrw(nativeGain, fxRate);
            }
        }
        BigDecimal krwValuation = BigDecimal.ZERO;
        if (!isZero(reportedEval) && looksLikeKrw(reportedEval, nativeValuation, fxRate)) {
            krwValuation = reportedEval;
        } else if (!isZero(krwPurchase) || !isZero(krwGain)) {
            krwValuation = krwPurchase.add(krwGain);
        } else {
            krwValuation = toKrw(nativeValuation, fxRate);
        }

        BigDecimal profitRate = firstDecimal(row, "evlu_pfls_rt1", "evlu_pfls_rt");
        if (isZero(profitRate) && !isZero(nativePurchase)) {
            profitRate = nativeGain.divide(nativePurchase, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"));
        }

        return new KisApiClient.KisPosition(
                itemCode,
                defaultString(text(row, "prdt_name", "ovrs_item_name", "item_name"), itemCode),
                "STOCK",
                itemCode,
                quantity,
                profitRate,
                defaultString(text(row, "buy_crcy_cd", "crcy_cd", "tr_crcy_cd"), "USD"),
                "Y",
                fxRate,
                new KisApiClient.NativeQuote(nativeAvg, nativePrice, nativePurchase, nativeValuation, nativeGain),
                new KisApiClient.KrwQuote(krwPurchase, krwValuation, krwGain));
    }

    private static List<KisApiClient.KisPosition> applyFxAndKrw(
            List<KisApiClient.KisPosition> source,
            Map<String, BigDecimal> fxByCurrency,
            BigDecimal derivedUsdKrw) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<KisApiClient.KisPosition> result = new ArrayList<>();
        for (KisApiClient.KisPosition position : source) {
            BigDecimal fx = position.fxRate();
            if (isZero(fx) && position.currencyCode() != null) {
                fx = fxByCurrency.get(position.currencyCode());
            }
            if (isZero(fx) && "USD".equalsIgnoreCase(position.currencyCode())) {
                fx = derivedUsdKrw;
            }
            result.add(withFx(position, fx));
        }
        return List.copyOf(result);
    }

    private static KisApiClient.KisPosition withFx(KisApiClient.KisPosition position, BigDecimal fxRate) {
        KisApiClient.NativeQuote nativeQuote = position.nativeQuote();
        KisApiClient.KrwQuote krw = rebuildKrw(nativeQuote, position.krw(), fxRate);
        return new KisApiClient.KisPosition(
                position.itemCode(),
                position.itemName(),
                position.productType(),
                position.productCode(),
                position.quantity(),
                position.profitRate(),
                position.currencyCode(),
                position.overseasYn(),
                isZero(fxRate) ? BigDecimal.ZERO : fxRate,
                nativeQuote,
                krw);
    }

    private static KisApiClient.KrwQuote rebuildKrw(
            KisApiClient.NativeQuote nativeQuote,
            KisApiClient.KrwQuote reported,
            BigDecimal fxRate) {
        BigDecimal nativePurchase = nativeQuote == null ? BigDecimal.ZERO : defaultDecimal(nativeQuote.purchaseAmount());
        BigDecimal nativeValuation = nativeQuote == null ? BigDecimal.ZERO : defaultDecimal(nativeQuote.valuationAmount());
        BigDecimal nativeGain = nativeQuote == null ? BigDecimal.ZERO : defaultDecimal(nativeQuote.gainLoss());

        BigDecimal krwPurchase = reported == null ? BigDecimal.ZERO : defaultDecimal(reported.purchaseAmount());
        BigDecimal krwValuation = reported == null ? BigDecimal.ZERO : defaultDecimal(reported.valuationAmount());
        BigDecimal krwGain = reported == null ? BigDecimal.ZERO : defaultDecimal(reported.gainLoss());

        if (!looksLikeKrw(krwPurchase, nativePurchase, fxRate)) {
            krwPurchase = toKrw(nativePurchase, fxRate);
        }
        if (!looksLikeKrw(krwValuation, nativeValuation, fxRate)) {
            krwValuation = toKrw(nativeValuation, fxRate);
        }
        if (!looksLikeKrw(krwGain, nativeGain, fxRate) || krwGain.abs().compareTo(krwValuation.abs()) > 0) {
            krwGain = krwValuation.subtract(krwPurchase);
        }
        return new KisApiClient.KrwQuote(krwPurchase, krwValuation, krwGain);
    }

    private static Map<String, BigDecimal> collectFxFromOutput2(JsonNode output2) {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        for (JsonNode row : asRows(output2)) {
            String currency = text(row, "crcy_cd", "buy_crcy_cd", "tr_crcy_cd");
            BigDecimal rate = firstDecimal(row, "bass_exrt", "frst_bltn_exrt", "exrt", "ovrs_exrt", "wcrc_exrt");
            if (currency == null || currency.isBlank() || isZero(rate)) {
                continue;
            }
            if (currency.length() > 3) {
                continue;
            }
            rates.putIfAbsent(currency.toUpperCase(), rate);
        }
        return rates;
    }

    private static BigDecimal deriveUsdKrw(
            List<KisApiClient.KisPosition> positions,
            BigDecimal krwPurchaseTotal,
            BigDecimal krwValuationTotal) {
        if (positions == null || positions.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal nativePurchase = BigDecimal.ZERO;
        BigDecimal nativeValuation = BigDecimal.ZERO;
        for (KisApiClient.KisPosition position : positions) {
            if (!"USD".equalsIgnoreCase(position.currencyCode()) || position.nativeQuote() == null) {
                continue;
            }
            nativePurchase = nativePurchase.add(defaultDecimal(position.nativeQuote().purchaseAmount()));
            nativeValuation = nativeValuation.add(defaultDecimal(position.nativeQuote().valuationAmount()));
        }
        BigDecimal fromPurchase = ratioAsFx(krwPurchaseTotal, nativePurchase);
        if (!isZero(fromPurchase)) {
            return fromPurchase;
        }
        return ratioAsFx(krwValuationTotal, nativeValuation);
    }

    private static BigDecimal ratioAsFx(BigDecimal krw, BigDecimal nativeAmount) {
        if (isZero(krw) || isZero(nativeAmount)) {
            return BigDecimal.ZERO;
        }
        BigDecimal ratio = krw.abs().divide(nativeAmount.abs(), 4, RoundingMode.HALF_UP);
        if (ratio.compareTo(new BigDecimal("100")) < 0 || ratio.compareTo(new BigDecimal("10000")) > 0) {
            return BigDecimal.ZERO;
        }
        return ratio;
    }

    private static BigDecimal defaultDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Map<String, BigDecimal> collectFxRates(List<KisApiClient.KisPosition> positions) {
        Map<String, BigDecimal> fxRates = new LinkedHashMap<>();
        for (KisApiClient.KisPosition position : positions) {
            if (position.currencyCode() == null || position.currencyCode().isBlank()) {
                continue;
            }
            if (position.fxRate() == null || isZero(position.fxRate())) {
                continue;
            }
            fxRates.putIfAbsent(position.currencyCode(), position.fxRate());
        }
        return fxRates;
    }

    private static boolean looksLikeKrw(BigDecimal amount, BigDecimal nativeAmount, BigDecimal fxRate) {
        if (isZero(amount)) {
            return false;
        }
        if (isZero(nativeAmount) || isZero(fxRate)) {
            return fxRate.compareTo(new BigDecimal("10")) > 0 && amount.abs().compareTo(new BigDecimal("1000")) > 0;
        }
        BigDecimal ratio = amount.abs().divide(nativeAmount.abs(), 4, RoundingMode.HALF_UP);
        return ratio.compareTo(new BigDecimal("8")) > 0;
    }

    private static BigDecimal toKrw(BigDecimal nativeAmount, BigDecimal fxRate) {
        if (isZero(nativeAmount) || isZero(fxRate)) {
            return BigDecimal.ZERO;
        }
        return nativeAmount.multiply(fxRate).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isZero(BigDecimal value) {
        return value == null || value.compareTo(BigDecimal.ZERO) == 0;
    }

    static void requireSuccess(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            throw ApiException.internalServerError("KIS 통신 오류가 발생했습니다.", "INVALID_KIS_RESPONSE");
        }
        String rtCd = root.path("rt_cd").asText("0");
        if (!"0".equals(rtCd)) {
            String msg = root.path("msg1").asText("KIS 통신 오류가 발생했습니다.");
            String code = root.path("msg_cd").asText("KIS_API_ERROR");
            throw ApiException.badRequest(msg, code);
        }
    }

    private static List<JsonNode> asRows(JsonNode node) {
        List<JsonNode> rows = new ArrayList<>();
        if (node == null || node.isMissingNode() || node.isNull()) {
            return rows;
        }
        if (node.isArray()) {
            node.forEach(rows::add);
            return rows;
        }
        if (node.isObject() && node.size() > 0) {
            rows.add(node);
        }
        return rows;
    }

    private static JsonNode firstObject(JsonNode node) {
        if (node != null && node.isArray() && !node.isEmpty()) {
            return node.get(0);
        }
        if (node != null && node.isObject()) {
            return node;
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private static java.math.BigDecimal firstDecimal(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            String text = value.asText("").trim();
            if (text.isEmpty()) {
                continue;
            }
            try {
                return new java.math.BigDecimal(text.replace(",", ""));
            } catch (NumberFormatException ignored) {
                // next
            }
        }
        return java.math.BigDecimal.ZERO;
    }

    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            String text = value.asText();
            if (text != null && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static String text(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
