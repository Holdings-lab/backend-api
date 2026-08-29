package com.project.server.service.integration.kis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.server.config.KisProperties;
import com.project.server.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
@ConditionalOnExpression("!'stub'.equalsIgnoreCase('${kis.api.mode:stub}')")
public class KisLiveClientService implements KisApiClient {

    private static final String BALANCE_PATH = "/uapi/domestic-stock/v1/trading/inquire-balance";
    private static final String PAPER_TR_ID = "VTTC8434R";
    private static final String REAL_TR_ID = "TTTC8434R";

    private final ObjectMapper objectMapper;
    private final KisProperties kisProperties;
    private final KisTokenService kisTokenService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Override
    public KisBalanceSnapshot fetchBalance(KisCredential credential) {
        validateCredential(credential);

        JsonNode firstPage = null;
        ArrayNode mergedHoldings = objectMapper.createArrayNode();
        String ctxAreaFk100 = "";
        String ctxAreaNk100 = "";

        for (int page = 0; page < 20; page++) {
            JsonNode pageNode = callBalance(credential, ctxAreaFk100, ctxAreaNk100, page == 0, page > 0);
            if (firstPage == null) {
                firstPage = pageNode;
            }
            appendHoldings(mergedHoldings, pageNode.path("output1"));
            ctxAreaFk100 = pageNode.path("ctx_area_fk100").asText("");
            ctxAreaNk100 = pageNode.path("ctx_area_nk100").asText("");
            boolean hasMore = hasMorePages(pageNode, ctxAreaNk100);
            ctxAreaFk100 = ctxAreaFk100.trim();
            ctxAreaNk100 = ctxAreaNk100.trim();
            if (!hasMore) {
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

    private JsonNode callBalance(
            KisCredential credential,
            String ctxAreaFk100,
            String ctxAreaNk100,
            boolean allowTokenRetry,
            boolean continuation) {
        int maxRetries = Math.max(1, kisProperties.getApi().getMaxRetries());
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                String accessToken = kisTokenService.getAccessToken(credential.appKey(), credential.appSecret());
                String url = baseUrl() + BALANCE_PATH + "?" + balanceQuery(credential, ctxAreaFk100, ctxAreaNk100);
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(kisProperties.getApi().getTimeoutSeconds()))
                        .header("content-type", "application/json; charset=utf-8")
                        .header("authorization", "Bearer " + accessToken)
                        .header("appkey", credential.appKey())
                        .header("appsecret", credential.appSecret())
                        .header("tr_id", trId())
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
                    log.error("KIS balance API error: {} - {}", response.statusCode(), response.body());
                    throw ApiException.internalServerError("한투 잔고조회에 실패했습니다.", "KIS_API_ERROR");
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
                throw ApiException.internalServerError("한투 API 요청이 중단되었습니다.", "KIS_INTERRUPTED");
            } catch (Exception e) {
                log.error("Error calling KIS balance API", e);
                if (attempt == maxRetries) {
                    throw ApiException.internalServerError("한투 잔고조회에 실패했습니다.", "KIS_API_ERROR");
                }
            }
        }
        throw ApiException.internalServerError("한투 잔고조회에 실패했습니다.", "KIS_API_ERROR");
    }

    private String balanceQuery(KisCredential credential, String ctxAreaFk100, String ctxAreaNk100) {
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

        List<String> pairs = new ArrayList<>();
        params.forEach((key, value) -> pairs.add(key + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)));
        return String.join("&", pairs);
    }

    private boolean isTokenExpired(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        return body.contains("EGW00123") || body.contains("기간이 만료된 token");
    }

    private String trId() {
        return kisProperties.isPaperMode() ? PAPER_TR_ID : REAL_TR_ID;
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

    private static boolean hasMorePages(JsonNode pageNode, String ctxAreaNk100) {
        String trCont = pageNode.path("_tr_cont").asText("").trim();
        if ("M".equalsIgnoreCase(trCont) || "F".equalsIgnoreCase(trCont)) {
            return true;
        }
        return ctxAreaNk100 != null && !ctxAreaNk100.isBlank();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
