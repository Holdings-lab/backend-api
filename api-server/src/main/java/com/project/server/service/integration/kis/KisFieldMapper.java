package com.project.server.service.integration.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;

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
                toPositions(output1));
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

    private static List<KisApiClient.KisPosition> toPositions(JsonNode output1) {
        List<KisApiClient.KisPosition> positions = new ArrayList<>();
        if (output1 == null || !output1.isArray()) {
            return positions;
        }
        for (JsonNode row : output1) {
            String itemCode = text(row, "pdno");
            if (itemCode == null || itemCode.isBlank()) {
                continue;
            }
            String overseasYn = itemCode.replaceAll("[^0-9]", "").length() == 6 ? "N" : "Y";
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
                    overseasYn));
        }
        return positions;
    }

    static void requireSuccess(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            throw ApiException.internalServerError("한투 API 응답이 올바르지 않습니다.", "INVALID_KIS_RESPONSE");
        }
        String rtCd = root.path("rt_cd").asText("0");
        if (!"0".equals(rtCd)) {
            String msg = root.path("msg1").asText("한투 API 호출 실패");
            String code = root.path("msg_cd").asText("KIS_API_ERROR");
            throw ApiException.badRequest(msg, code);
        }
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
}
