package com.project.server.service.integration.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.dto.BrokerAccountDto;
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
            JsonNode overseasOutput3) {
        List<KisApiClient.KisPosition> positions = overseasPositions == null
                ? List.of()
                : List.copyOf(overseasPositions);

        JsonNode output3 = firstObject(overseasOutput3);
        BigDecimal cash = firstDecimal(output3, "tot_dncl_amt", "dncl_amt");
        BigDecimal evaluation = firstDecimal(output3, "evlu_amt_smtl", "evlu_amt_smtl_amt", "frcr_evlu_tota");
        BigDecimal purchase = firstDecimal(output3, "pchs_amt_smtl", "pchs_amt_smtl_amt");
        BigDecimal gainLoss = firstDecimal(output3, "evlu_pfls_amt_smtl", "tot_evlu_pfls_amt");
        BigDecimal total = firstDecimal(output3, "tot_asst_amt");
        if (total.compareTo(BigDecimal.ZERO) == 0) {
            total = evaluation.add(cash);
        }
        BigDecimal gainLossRate = purchase.compareTo(BigDecimal.ZERO) > 0
                ? gainLoss.divide(purchase, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

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

    public static String toFxRatesJson(Map<String, BigDecimal> fxRates) {
        if (fxRates == null || fxRates.isEmpty()) {
            return "{}";
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, BigDecimal> entry : fxRates.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            if (!first) {
                json.append(',');
            }
            json.append('"').append(entry.getKey()).append("\":").append(entry.getValue().toPlainString());
            first = false;
        }
        return json.append('}').toString();
    }

    public static Map<String, BigDecimal> parseFxRates(String json) {
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return rates;
        }
        try {
            JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            node.fields().forEachRemaining(entry -> {
                try {
                    rates.put(entry.getKey(), new BigDecimal(entry.getValue().asText()));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            });
        } catch (Exception ignored) {
            return Map.of();
        }
        return rates;
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

    public static BrokerAccountDto.AccountSnapshot toAccountSnapshot(BrokerAccountEntity entity, Map<String, Object> details) {
        String cano = stringValue(details, "cano");
        String product = firstString(details, "accountProductCode", "acntPrdtCd");
        if (product == null) {
            product = entity.getAccountProductCode();
        }
        String display = firstString(details, "accountDisplay", "acctDisp");
        if (display == null && cano != null && product != null) {
            display = KisAccountParser.display(cano, product);
        }
        return BrokerAccountDto.AccountSnapshot.builder()
                .accountDisplay(display)
                .accountName(firstString(details, "accountName", "acctNm"))
                .accountNick(firstString(details, "accountNick", "acctNick"))
                .balance(firstString(details, "balance", "nassAmt"))
                .currencyCode(firstString(details, "currencyCode", "curCd"))
                .availableBalance(firstString(details, "availableBalance", "ablBal", "dncaTotAmt"))
                .accountHolder(firstString(details, "accountHolder", "acctHolder"))
                .accountProductCode(product)
                .cano(cano)
                .build();
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

        BigDecimal fxRate = firstDecimal(row, "bass_exrt", "frst_bltn_exrt");
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

    private static String stringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static String firstString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = stringValue(map, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
