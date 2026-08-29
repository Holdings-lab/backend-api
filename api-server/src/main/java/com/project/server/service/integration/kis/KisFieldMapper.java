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

    public static KisApiClient.KisBalanceSnapshot toSnapshot(KisApiClient.KisCredential credential, JsonNode root) {
        JsonNode output2 = firstObject(root.path("output2"));
        JsonNode output1 = root.path("output1");

        String cano = credential.cano();
        String productCode = credential.accountProductCode();
        String display = KisAccountParser.display(cano, productCode);

        return new KisApiClient.KisBalanceSnapshot(
                cano,
                productCode,
                display,
                decimal(output2, "dnca_tot_amt"),
                firstDecimal(output2, "nass_amt", "tot_evlu_amt"),
                firstDecimal(output2, "tot_evlu_amt", "evlu_amt_smtl_amt"),
                firstDecimal(output2, "pchs_amt_smtl_amt", "pchs_amt_smtl"),
                firstDecimal(output2, "evlu_pfls_smtl_amt"),
                firstDecimal(output2, "asst_icdc_erng_rt", "evlu_pfls_rt"),
                toDomesticPositions(output1));
    }

    public static KisApiClient.KisBalanceSnapshot mergeOverseas(
            KisApiClient.KisBalanceSnapshot domestic,
            List<KisApiClient.KisPosition> overseasPositions,
            JsonNode overseasOutput3) {
        List<KisApiClient.KisPosition> merged = new ArrayList<>();
        if (domestic.positions() != null) {
            merged.addAll(domestic.positions());
        }
        if (overseasPositions != null) {
            merged.addAll(overseasPositions);
        }

        JsonNode output3 = firstObject(overseasOutput3);
        BigDecimal overseasCash = firstDecimal(output3, "tot_dncl_amt", "dncl_amt");
        BigDecimal overseasEval = firstDecimal(output3, "evlu_amt_smtl", "evlu_amt_smtl_amt", "frcr_evlu_tota");
        BigDecimal overseasPurchase = firstDecimal(output3, "pchs_amt_smtl", "pchs_amt_smtl_amt");
        BigDecimal overseasGain = firstDecimal(output3, "evlu_pfls_amt_smtl", "tot_evlu_pfls_amt");
        BigDecimal overseasTotal = firstDecimal(output3, "tot_asst_amt");
        if (overseasTotal.compareTo(BigDecimal.ZERO) == 0) {
            overseasTotal = overseasEval.add(overseasCash);
        }

        BigDecimal cash = nz(domestic.cashBalance()).add(overseasCash);
        BigDecimal evaluation = nz(domestic.evaluationAmount()).add(overseasEval);
        BigDecimal purchase = nz(domestic.purchaseAmount()).add(overseasPurchase);
        BigDecimal gainLoss = nz(domestic.gainLoss()).add(overseasGain);
        BigDecimal total = nz(domestic.totalAssetValue()).add(overseasTotal);
        BigDecimal gainLossRate = purchase.compareTo(BigDecimal.ZERO) > 0
                ? gainLoss.divide(purchase, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : nz(domestic.gainLossRate());

        return new KisApiClient.KisBalanceSnapshot(
                domestic.cano(),
                domestic.accountProductCode(),
                domestic.accountDisplay(),
                cash,
                total,
                evaluation,
                purchase,
                gainLoss,
                gainLossRate,
                merged);
    }

    public static List<KisApiClient.KisPosition> toOverseasPresentPositions(JsonNode output1) {
        List<KisApiClient.KisPosition> positions = new ArrayList<>();
        for (JsonNode row : asRows(output1)) {
            String itemCode = text(row, "pdno", "ovrs_pdno", "std_pdno");
            if (itemCode == null || itemCode.isBlank()) {
                continue;
            }
            BigDecimal quantity = firstDecimal(row, "cblc_qty13", "ovrs_cblc_qty", "cblc_qty", "hldg_qty");
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            positions.add(new KisApiClient.KisPosition(
                    itemCode,
                    defaultString(text(row, "prdt_name", "ovrs_item_name", "item_name"), itemCode),
                    "STOCK",
                    itemCode,
                    quantity,
                    firstDecimal(row, "avg_unpr3", "pchs_avg_pric"),
                    firstDecimal(row, "ovrs_now_pric1", "now_pric2", "prpr"),
                    firstDecimal(row, "frcr_evlu_amt2", "ovrs_stck_evlu_amt", "evlu_amt"),
                    firstDecimal(row, "pchs_rmnd_wcrc_amt", "frcr_pchs_amt", "frcr_pchs_amt1", "pchs_amt"),
                    firstDecimal(row, "evlu_pfls_amt2", "frcr_evlu_pfls_amt", "evlu_pfls_amt"),
                    firstDecimal(row, "evlu_pfls_rt1", "evlu_pfls_rt"),
                    defaultString(text(row, "buy_crcy_cd", "crcy_cd", "tr_crcy_cd"), "USD"),
                    "Y"));
        }
        return positions;
    }

    public static List<KisApiClient.KisPosition> toOverseasBalancePositions(JsonNode output1) {
        List<KisApiClient.KisPosition> positions = new ArrayList<>();
        for (JsonNode row : asRows(output1)) {
            String itemCode = text(row, "ovrs_pdno", "pdno", "std_pdno");
            if (itemCode == null || itemCode.isBlank()) {
                continue;
            }
            BigDecimal quantity = firstDecimal(row, "ovrs_cblc_qty", "cblc_qty", "hldg_qty");
            if (quantity.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            positions.add(new KisApiClient.KisPosition(
                    itemCode,
                    defaultString(text(row, "ovrs_item_name", "prdt_name", "item_name"), itemCode),
                    "STOCK",
                    itemCode,
                    quantity,
                    firstDecimal(row, "pchs_avg_pric", "avg_unpr3"),
                    firstDecimal(row, "now_pric2", "ovrs_now_pric1", "prpr"),
                    firstDecimal(row, "ovrs_stck_evlu_amt", "frcr_evlu_amt2", "evlu_amt"),
                    firstDecimal(row, "frcr_pchs_amt1", "frcr_pchs_amt", "pchs_amt"),
                    firstDecimal(row, "frcr_evlu_pfls_amt", "evlu_pfls_amt2", "evlu_pfls_amt"),
                    firstDecimal(row, "evlu_pfls_rt", "evlu_pfls_rt1"),
                    defaultString(text(row, "tr_crcy_cd", "buy_crcy_cd", "crcy_cd"), "USD"),
                    "Y"));
        }
        return positions;
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

    private static List<KisApiClient.KisPosition> toDomesticPositions(JsonNode output1) {
        List<KisApiClient.KisPosition> positions = new ArrayList<>();
        for (JsonNode row : asRows(output1)) {
            String itemCode = text(row, "pdno", "item_cd", "stck_shrn_iscd");
            if (itemCode == null || itemCode.isBlank()) {
                continue;
            }
            positions.add(new KisApiClient.KisPosition(
                    itemCode,
                    text(row, "prdt_name"),
                    defaultString(text(row, "trad_dvsn_name"), "STOCK"),
                    text(row, "prdt_cd", "pdno"),
                    decimal(row, "hldg_qty"),
                    decimal(row, "pchs_avg_pric"),
                    decimal(row, "prpr"),
                    decimal(row, "evlu_amt"),
                    decimal(row, "pchs_amt"),
                    decimal(row, "evlu_pfls_amt"),
                    decimal(row, "evlu_pfls_rt"),
                    "KRW",
                    "N"));
        }
        return positions;
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

    private static java.math.BigDecimal decimal(JsonNode node, String key) {
        return firstDecimal(node, key);
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

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
