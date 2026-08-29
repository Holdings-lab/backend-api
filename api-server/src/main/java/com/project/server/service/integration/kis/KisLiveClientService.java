package com.project.server.service.integration.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.server.config.KisProperties;
import com.project.server.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KisLiveClientService implements KisApiClient {

    private static final String KIS_COMMUNICATION_ERROR = "KIS 통신 오류가 발생했습니다.";

    private static final String DOMESTIC_BALANCE_PATH = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String OVERSEAS_PRESENT_PATH = "/uapi/overseas-stock/v1/trading/inquire-present-balance";
    private static final String OVERSEAS_BALANCE_PATH = "/uapi/overseas-stock/v1/trading/inquire-balance";

    private static final String DOMESTIC_PAPER_TR = "VTTC8434R";
    private static final String DOMESTIC_REAL_TR = "TTTC8434R";
    private static final String OVERSEAS_PRESENT_PAPER_TR = "VTRP6504R";
    private static final String OVERSEAS_PRESENT_REAL_TR = "CTRP6504R";
    private static final String OVERSEAS_BALANCE_PAPER_TR = "VTTS3012R";
    private static final String OVERSEAS_BALANCE_REAL_TR = "TTTS3012R";

    /**
     * 모의는 NASD가 나스닥만, 실전은 미국전체. NYSE/AMEX도 따로 조회한다.
     * 체결기준현재잔고가 실패했을 때만 사용.
     */
    private static final List<OverseasMarket> OVERSEAS_MARKETS = List.of(
            new OverseasMarket("NASD", "USD"),
            new OverseasMarket("NYSE", "USD"),
            new OverseasMarket("AMEX", "USD"),
            new OverseasMarket("SEHK", "HKD"),
            new OverseasMarket("SHAA", "CNY"),
            new OverseasMarket("SZAA", "CNY"),
            new OverseasMarket("TKSE", "JPY"),
            new OverseasMarket("HASE", "VND"),
            new OverseasMarket("VNSE", "VND"));

    private final ObjectMapper objectMapper;
    private final KisProperties kisProperties;
    private final KisTokenService kisTokenService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public KisBalanceSnapshot fetchBalance(KisCredential credential) {
        validateCredential(credential);
        KisBalanceSnapshot domestic = fetchDomesticBalance(credential);
        OverseasHoldings overseas = fetchOverseasHoldings(credential);
        if (overseas.positions().isEmpty()) {
            log.info("[KIS] overseas holdings empty. CANO={} ACNT_PRDT_CD={}",
                    credential.cano(), credential.accountProductCode());
        } else {
            log.info("[KIS] merged overseas holdings count={}", overseas.positions().size());
        }
        return KisFieldMapper.mergeOverseas(domestic, overseas.positions(), overseas.output3());
    }

    private KisBalanceSnapshot fetchDomesticBalance(KisCredential credential) {
        JsonNode firstPage = null;
        ArrayNode mergedHoldings = objectMapper.createArrayNode();
        String ctxAreaFk100 = "";
        String ctxAreaNk100 = "";

        for (int page = 0; page < 20; page++) {
            Map<String, String> query = domesticBalanceQuery(credential, ctxAreaFk100, ctxAreaNk100);
            JsonNode pageNode = callGet(
                    credential,
                    DOMESTIC_BALANCE_PATH,
                    domesticTrId(),
                    query,
                    page == 0,
                    page > 0);
            if (firstPage == null) {
                firstPage = pageNode;
            }
            appendHoldings(mergedHoldings, pageNode.path("output1"));
            ctxAreaFk100 = pageNode.path("ctx_area_fk100").asText("").trim();
            ctxAreaNk100 = pageNode.path("ctx_area_nk100").asText("").trim();
            if (!hasMorePages(pageNode, ctxAreaNk100)) {
                break;
            }
        }

        ObjectNode merged = firstPage instanceof ObjectNode objectNode
                ? objectNode.deepCopy()
                : objectMapper.createObjectNode();
        merged.set("output1", mergedHoldings);
        if (firstPage != null && firstPage.has("output2")) {
            merged.set("output2", firstPage.get("output2"));
        }
        if (mergedHoldings.isEmpty()) {
            log.warn("[KIS] inquire-balance returned no holdings. CANO={} ACNT_PRDT_CD={} output1={} output2={}",
                    credential.cano(),
                    credential.accountProductCode(),
                    firstPage == null ? null : firstPage.path("output1"),
                    firstPage == null ? null : firstPage.path("output2"));
        }
        return KisFieldMapper.toSnapshot(credential, merged);
    }

    private OverseasHoldings fetchOverseasHoldings(KisCredential credential) {
        try {
            JsonNode present = fetchOverseasPresentBalance(credential);
            List<KisPosition> positions = KisFieldMapper.toOverseasPresentPositions(present.path("output1"));
            if (positions.isEmpty() && kisProperties.isPaperMode()) {
                log.info("[KIS] paper inquire-present-balance empty, trying exchange inquire-balance");
                List<KisPosition> byExchange = fetchOverseasByExchange(credential);
                if (!byExchange.isEmpty()) {
                    positions = byExchange;
                }
            }
            return new OverseasHoldings(positions, present.path("output3"));
        } catch (ApiException e) {
            log.warn("[KIS] inquire-present-balance failed, falling back to exchange inquire-balance: {}",
                    e.getMessage());
        } catch (Exception e) {
            log.warn("[KIS] inquire-present-balance failed, falling back to exchange inquire-balance", e);
        }
        return new OverseasHoldings(
                fetchOverseasByExchange(credential),
                com.fasterxml.jackson.databind.node.MissingNode.getInstance());
    }

    private JsonNode fetchOverseasPresentBalance(KisCredential credential) {
        JsonNode firstPage = null;
        ArrayNode mergedHoldings = objectMapper.createArrayNode();
        JsonNode output3 = objectMapper.missingNode();

        for (int page = 0; page < 20; page++) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("CANO", credential.cano());
            query.put("ACNT_PRDT_CD", credential.accountProductCode());
            query.put("WCRC_FRCR_DVSN_CD", "01");
            query.put("NATN_CD", "000");
            query.put("TR_MKET_CD", "00");
            query.put("INQR_DVSN_CD", "00");

            JsonNode pageNode = callGet(
                    credential,
                    OVERSEAS_PRESENT_PATH,
                    overseasPresentTrId(),
                    query,
                    page == 0,
                    page > 0);
            if (firstPage == null) {
                firstPage = pageNode;
            }
            appendHoldings(mergedHoldings, pageNode.path("output1"));
            if (pageNode.has("output3") && !pageNode.path("output3").isMissingNode()) {
                output3 = pageNode.get("output3");
            }
            String ctxNk = pageNode.path("ctx_area_nk200").asText(
                    pageNode.path("ctx_area_nk100").asText("")).trim();
            if (!hasMorePages(pageNode, ctxNk)) {
                break;
            }
        }

        ObjectNode merged = firstPage instanceof ObjectNode objectNode
                ? objectNode.deepCopy()
                : objectMapper.createObjectNode();
        merged.set("output1", mergedHoldings);
        if (output3 != null && !output3.isMissingNode()) {
            merged.set("output3", output3);
        }
        return merged;
    }

    private List<KisPosition> fetchOverseasByExchange(KisCredential credential) {
        Map<String, KisPosition> byCode = new LinkedHashMap<>();
        for (OverseasMarket market : OVERSEAS_MARKETS) {
            try {
                JsonNode node = fetchOverseasBalanceForMarket(credential, market);
                for (KisPosition position : KisFieldMapper.toOverseasBalancePositions(node.path("output1"))) {
                    byCode.putIfAbsent(position.itemCode(), position);
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw ApiException.internalServerError(KIS_COMMUNICATION_ERROR, "KIS_INTERRUPTED");
            } catch (ApiException e) {
                log.warn("[KIS] overseas inquire-balance skipped {}/{}: {}",
                        market.exchange(), market.currency(), e.getMessage());
            } catch (Exception e) {
                log.warn("[KIS] overseas inquire-balance skipped {}/{}",
                        market.exchange(), market.currency(), e);
            }
        }
        return new ArrayList<>(byCode.values());
    }

    private JsonNode fetchOverseasBalanceForMarket(KisCredential credential, OverseasMarket market) {
        JsonNode firstPage = null;
        ArrayNode mergedHoldings = objectMapper.createArrayNode();
        String ctxAreaFk200 = "";
        String ctxAreaNk200 = "";

        for (int page = 0; page < 20; page++) {
            Map<String, String> query = new LinkedHashMap<>();
            query.put("CANO", credential.cano());
            query.put("ACNT_PRDT_CD", credential.accountProductCode());
            query.put("OVRS_EXCG_CD", market.exchange());
            query.put("TR_CRCY_CD", market.currency());
            query.put("CTX_AREA_FK200", ctxAreaFk200);
            query.put("CTX_AREA_NK200", ctxAreaNk200);

            JsonNode pageNode = callGet(
                    credential,
                    OVERSEAS_BALANCE_PATH,
                    overseasBalanceTrId(),
                    query,
                    page == 0,
                    page > 0);
            if (firstPage == null) {
                firstPage = pageNode;
            }
            appendHoldings(mergedHoldings, pageNode.path("output1"));
            ctxAreaFk200 = pageNode.path("ctx_area_fk200").asText("").trim();
            ctxAreaNk200 = pageNode.path("ctx_area_nk200").asText("").trim();
            if (!hasMorePages(pageNode, ctxAreaNk200)) {
                break;
            }
        }

        ObjectNode merged = firstPage instanceof ObjectNode objectNode
                ? objectNode.deepCopy()
                : objectMapper.createObjectNode();
        merged.set("output1", mergedHoldings);
        return merged;
    }

    private JsonNode callGet(
            KisCredential credential,
            String path,
            String trId,
            Map<String, String> query,
            boolean allowTokenRetry,
            boolean continuation) {
        int maxRetries = Math.max(1, kisProperties.getApi().getMaxRetries());
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String accessToken = kisTokenService.getAccessToken(credential.appKey(), credential.appSecret());
                String url = baseUrl() + path + "?" + encodeQuery(query);
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(kisProperties.getApi().getTimeoutSeconds()))
                        .header("content-type", "application/json; charset=utf-8")
                        .header("authorization", "Bearer " + accessToken)
                        .header("appkey", credential.appKey())
                        .header("appsecret", credential.appSecret())
                        .header("tr_id", trId)
                        .header("custtype", "P")
                        .GET();
                if (continuation) {
                    builder.header("tr_cont", "N");
                }

                HttpResponse<String> response = httpClient.send(builder.build(),
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                if ((response.statusCode() == 401 || isTokenExpired(response.body()))
                        && allowTokenRetry
                        && attempt < maxRetries) {
                    kisTokenService.invalidate(credential.appKey());
                    log.warn("KIS token expired on attempt {}, refreshing...", attempt);
                    continue;
                }

                if (response.statusCode() >= 500 && attempt < maxRetries) {
                    log.warn("KIS server error on attempt {}, retrying...", attempt);
                    Thread.sleep(1000L * attempt);
                    continue;
                }

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.error("KIS API error {} {}: {} - {}", trId, path, response.statusCode(), response.body());
                    throw ApiException.internalServerError(KIS_COMMUNICATION_ERROR, "KIS_API_ERROR");
                }

                JsonNode root = objectMapper.readTree(response.body());
                KisFieldMapper.requireSuccess(root);
                String trCont = response.headers().firstValue("tr_cont").orElse("");
                if (root instanceof ObjectNode objectNode && !trCont.isBlank()) {
                    objectNode.put("_tr_cont", trCont.trim());
                }
                return root;
            } catch (ApiException ae) {
                throw ae;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw ApiException.internalServerError(KIS_COMMUNICATION_ERROR, "KIS_INTERRUPTED");
            } catch (Exception e) {
                log.error("Error calling KIS API {} {}", trId, path, e);
                if (attempt == maxRetries) {
                    throw ApiException.internalServerError(KIS_COMMUNICATION_ERROR, "KIS_API_ERROR");
                }
            }
        }
        throw ApiException.internalServerError(KIS_COMMUNICATION_ERROR, "KIS_API_ERROR");
    }

    private Map<String, String> domesticBalanceQuery(
            KisCredential credential,
            String ctxAreaFk100,
            String ctxAreaNk100) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("CANO", credential.cano());
        params.put("ACNT_PRDT_CD", credential.accountProductCode());
        params.put("AFHR_FLPR_YN", "N");
        params.put("OFL_YN", "");
        params.put("INQR_DVSN", "02");
        params.put("UNPR_DVSN", "01");
        params.put("FUND_STTL_ICLD_YN", "N");
        params.put("FNCG_AMT_AUTO_RDPT_YN", "N");
        params.put("PRCS_DVSN", "00");
        params.put("CTX_AREA_FK100", ctxAreaFk100 == null ? "" : ctxAreaFk100);
        params.put("CTX_AREA_NK100", ctxAreaNk100 == null ? "" : ctxAreaNk100);
        return params;
    }

    private static String encodeQuery(Map<String, String> params) {
        List<String> pairs = new ArrayList<>();
        params.forEach((key, value) -> pairs.add(key + "=" + URLEncoder.encode(
                value == null ? "" : value, StandardCharsets.UTF_8)));
        return String.join("&", pairs);
    }

    private boolean isTokenExpired(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        return body.contains("EGW00123") || body.contains("기간이 만료된 token");
    }

    private String domesticTrId() {
        return kisProperties.isPaperMode() ? DOMESTIC_PAPER_TR : DOMESTIC_REAL_TR;
    }

    private String overseasPresentTrId() {
        return kisProperties.isPaperMode() ? OVERSEAS_PRESENT_PAPER_TR : OVERSEAS_PRESENT_REAL_TR;
    }

    private String overseasBalanceTrId() {
        return kisProperties.isPaperMode() ? OVERSEAS_BALANCE_PAPER_TR : OVERSEAS_BALANCE_REAL_TR;
    }

    private String baseUrl() {
        return kisProperties.isPaperMode()
                ? kisProperties.getApi().getPaperBaseUrl()
                : kisProperties.getApi().getRealBaseUrl();
    }

    private void validateCredential(KisCredential credential) {
        if (credential == null
                || isBlank(credential.appKey())
                || isBlank(credential.appSecret())
                || isBlank(credential.cano())
                || isBlank(credential.accountProductCode())) {
            throw ApiException.badRequest("한투 연동 정보가 누락되었습니다.", "KIS_CREDENTIAL_MISSING");
        }
    }

    private static void appendHoldings(ArrayNode target, JsonNode output1) {
        if (output1 == null || output1.isMissingNode() || output1.isNull()) {
            return;
        }
        if (output1.isTextual() && output1.asText().isBlank()) {
            return;
        }
        if (output1.isArray()) {
            output1.forEach(target::add);
            return;
        }
        if (output1.isObject() && output1.size() > 0) {
            target.add(output1);
        }
    }

    private static boolean hasMorePages(JsonNode pageNode, String ctxAreaNk) {
        String trCont = pageNode.path("_tr_cont").asText("").trim();
        if ("M".equalsIgnoreCase(trCont) || "F".equalsIgnoreCase(trCont)) {
            return true;
        }
        return ctxAreaNk != null && !ctxAreaNk.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record OverseasMarket(String exchange, String currency) {
    }

    private record OverseasHoldings(List<KisPosition> positions, JsonNode output3) {
    }
}
