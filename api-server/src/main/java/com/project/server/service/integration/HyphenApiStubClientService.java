package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 하이픈 API 스텁 (한국투자증권 KIS / bankCd 0243 기준).
 * 실제 HTTP 호출 없이 요청을 검증하고 OpenAPI 명세 필드만 포함한 더미 응답을 반환합니다.
 *
 * @see <a href="https://www.hyphen.im/product-api/view?seq=96">개인계좌 증권사 조회 API
 *      명세</a>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hyphen.api.mode", havingValue = "stub")
public class HyphenApiStubClientService implements HyphenApiClient {

    private static final String KIS_ACCT_NO = "4312345601";
    private static final String KIS_ACCT_NO_CMA = "4312345602";
    private static final String KIS_ACCT_DISP = "4312-3456-01";
    private static final String KIS_ACCT_DISP_CMA = "4312-3456-02";

    private final ObjectMapper objectMapper;

    /** 전계좌조회 - 전계좌 목록 (in0104000534) */
    @Override
    public JsonNode fetchAccountList(HyphenCredential credential, String brokerName) {
        validateHyphenCredential(credential);
        requireKisBroker(brokerName);
        log.info("[Hyphen STUB] KIS 전계좌조회");

        return parseJson("""
                {
                  "common": %s,
                  "data": {
                    "list": [
                      {
                        "acctNo": "%s",
                        "acctNm": "종합위탁",
                        "acctNick": "한국투자 위탁",
                        "acctDisp": "%s",
                        "openDt": "20180315",
                        "endDt": "",
                        "lastDt": "20250628",
                        "balance": "1500000",
                        "curCd": "KRW",
                        "dormantYn": "N",
                        "ablBal": "1480000",
                        "acctHolder": "홍길동"
                      },
                      {
                        "acctNo": "%s",
                        "acctNm": "CMA",
                        "acctNick": "한국투자 CMA",
                        "acctDisp": "%s",
                        "openDt": "20200901",
                        "endDt": "",
                        "lastDt": "20250628",
                        "balance": "500000",
                        "curCd": "KRW",
                        "dormantYn": "N",
                        "ablBal": "500000",
                        "acctHolder": "홍길동"
                      }
                    ]
                  }
                }
                """.formatted(stubCommonJson(), KIS_ACCT_NO, KIS_ACCT_DISP, KIS_ACCT_NO_CMA, KIS_ACCT_DISP_CMA));
    }

    /** 잔액조회 - 입출금/외화/대출 잔액 (in0104000536) */
    @Override
    public JsonNode fetchCashBalance(HyphenCredential credential, String brokerName, String accountNumber) {
        validateHyphenCredential(credential);
        requireKisBroker(brokerName);
        requireAccountNumber(accountNumber);
        log.info("[Hyphen STUB] KIS 잔액조회 acctNo={}", accountNumber);

        return parseJson("""
                {
                  "common": %s,
                  "data": {
                    "curCd": "KRW",
                    "curBal": "18420000"
                  }
                }
                """.formatted(stubCommonJson()));
    }

    /** 잔고조회 - 보유종목·평가금액 (in0104000539) */
    @Override
    public JsonNode fetchHoldings(HyphenCredential credential, String brokerName, String accountNumber) {
        validateHyphenCredential(credential);
        requireKisBroker(brokerName);
        requireAccountNumber(accountNumber);
        log.info("[Hyphen STUB] KIS 잔고조회 acctNo={}", accountNumber);

        return parseJson("""
                {
                  "common": %s,
                  "data": {
                    "list": [
                      {
                        "acctNm": "종합위탁",
                        "acctNo": "%s",
                        "acctHolder": "홍길동",
                        "totPurchaseAmt": "17000000",
                        "totValuationAmt": "18420000",
                        "totValuationGL": "1420000",
                        "totProfitRate": "8.35",
                        "estDepAsset": "18420000",
                        "itemList": [
                          {
                            "productType": "해외주식",
                            "productCd": "02",
                            "itemNm": "Invesco QQQ Trust",
                            "itemCd": "QQQ",
                            "quantity": "15",
                            "purchaseUnitPrice": "380.00",
                            "purchaseAmt": "5400000",
                            "purchaseAmt_KRW": "5400000",
                            "presentAmt": "419.98",
                            "valuationAmt": "6299640",
                            "valuationAmt_KRW": "6299640",
                            "valuationGL": "899640",
                            "profitRate": "16.66",
                            "curCd": "USD",
                            "exYn": "Y",
                            "curList": [
                              {
                                "curCd": "USD",
                                "exRate": "1350",
                                "balance": "6299640",
                                "lqdProfitLoss": "899640",
                                "fees": "0",
                                "valuationGL": "899640",
                                "estDepAsset": "6299640",
                                "totOrdQnt": "15",
                                "totWthdrAmt": "0"
                              }
                            ]
                          },
                          {
                            "productType": "해외주식",
                            "productCd": "02",
                            "itemNm": "iShares Semiconductor",
                            "itemCd": "SOXX",
                            "quantity": "12",
                            "purchaseUnitPrice": "280.00",
                            "purchaseAmt": "3360000",
                            "purchaseAmt_KRW": "3360000",
                            "presentAmt": "333.10",
                            "valuationAmt": "3997140",
                            "valuationAmt_KRW": "3997140",
                            "valuationGL": "637140",
                            "profitRate": "18.96",
                            "curCd": "USD",
                            "exYn": "Y",
                            "curList": [
                              {
                                "curCd": "USD",
                                "exRate": "1350",
                                "balance": "3997140",
                                "lqdProfitLoss": "637140",
                                "fees": "0",
                                "valuationGL": "637140",
                                "estDepAsset": "3997140",
                                "totOrdQnt": "12",
                                "totWthdrAmt": "0"
                              }
                            ]
                          },
                          {
                            "productType": "해외주식",
                            "productCd": "02",
                            "itemNm": "Financial Select SPDR",
                            "itemCd": "XLF",
                            "quantity": "50",
                            "purchaseUnitPrice": "48.00",
                            "purchaseAmt": "2400000",
                            "purchaseAmt_KRW": "2400000",
                            "presentAmt": "55.26",
                            "valuationAmt": "2763000",
                            "valuationAmt_KRW": "2763000",
                            "valuationGL": "363000",
                            "profitRate": "15.13",
                            "curCd": "USD",
                            "exYn": "Y",
                            "curList": [
                              {
                                "curCd": "USD",
                                "exRate": "1350",
                                "balance": "2763000",
                                "lqdProfitLoss": "363000",
                                "fees": "0",
                                "valuationGL": "363000",
                                "estDepAsset": "2763000",
                                "totOrdQnt": "50",
                                "totWthdrAmt": "0"
                              }
                            ]
                          },
                          {
                            "productType": "해외주식",
                            "productCd": "02",
                            "itemNm": "iShares Russell 2000 ETF",
                            "itemCd": "IWM",
                            "quantity": "8",
                            "purchaseUnitPrice": "220.00",
                            "purchaseAmt": "1760000",
                            "purchaseAmt_KRW": "1760000",
                            "presentAmt": "250.00",
                            "valuationAmt": "2000000",
                            "valuationAmt_KRW": "2000000",
                            "valuationGL": "240000",
                            "profitRate": "13.64",
                            "curCd": "USD",
                            "exYn": "Y",
                            "curList": [
                              {
                                "curCd": "USD",
                                "exRate": "1350",
                                "balance": "2000000",
                                "lqdProfitLoss": "240000",
                                "fees": "0",
                                "valuationGL": "240000",
                                "estDepAsset": "2000000",
                                "totOrdQnt": "8",
                                "totWthdrAmt": "0"
                              }
                            ]
                          },
                          {
                            "productType": "해외주식",
                            "productCd": "02",
                            "itemNm": "Vanguard S&P 500 ETF",
                            "itemCd": "VOO",
                            "quantity": "3",
                            "purchaseUnitPrice": "520.00",
                            "purchaseAmt": "1560000",
                            "purchaseAmt_KRW": "1560000",
                            "presentAmt": "600.00",
                            "valuationAmt": "1800000",
                            "valuationAmt_KRW": "1800000",
                            "valuationGL": "240000",
                            "profitRate": "15.38",
                            "curCd": "USD",
                            "exYn": "Y",
                            "curList": [
                              {
                                "curCd": "USD",
                                "exRate": "1350",
                                "balance": "1800000",
                                "lqdProfitLoss": "240000",
                                "fees": "0",
                                "valuationGL": "240000",
                                "estDepAsset": "1800000",
                                "totOrdQnt": "3",
                                "totWthdrAmt": "0"
                              }
                            ]
                          },
                          {
                            "productType": "해외주식",
                            "productCd": "02",
                            "itemNm": "ARK Innovation ETF",
                            "itemCd": "ARKK",
                            "quantity": "40",
                            "purchaseUnitPrice": "35.00",
                            "purchaseAmt": "1400000",
                            "purchaseAmt_KRW": "1400000",
                            "presentAmt": "39.01",
                            "valuationAmt": "1560220",
                            "valuationAmt_KRW": "1560220",
                            "valuationGL": "160220",
                            "profitRate": "11.44",
                            "curCd": "USD",
                            "exYn": "Y",
                            "curList": [
                              {
                                "curCd": "USD",
                                "exRate": "1350",
                                "balance": "1560220",
                                "lqdProfitLoss": "160220",
                                "fees": "0",
                                "valuationGL": "160220",
                                "estDepAsset": "1560220",
                                "totOrdQnt": "40",
                                "totWthdrAmt": "0"
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  }
                }
                """.formatted(stubCommonJson(), defaultString(accountNumber, KIS_ACCT_NO)));
    }

    /**
     * 거래내역조회 - 입출금·적요 (in0104000535)
     * 한국투자증권에 없는 일부 필드 제외
     */
    @Override
    public JsonNode fetchDepositWithdrawHistory(
            HyphenCredential credential,
            String brokerName,
            String accountNumber,
            String fromDate,
            String toDate) {
        validateHyphenCredential(credential);
        requireKisBroker(brokerName);
        validateDateRange(fromDate, toDate);
        requireAccountNumber(accountNumber);
        log.info("[Hyphen STUB] KIS 거래내역조회 acctNo={}, {}~{}", accountNumber, fromDate, toDate);

        String acctNo = defaultString(accountNumber, KIS_ACCT_NO);

        return parseJson("""
                {
                  "common": %s,
                  "data": {
                    "acctNo": "%s",
                    "acctNm": "종합위탁",
                    "acctNick": "한국투자 위탁",
                    "curCd": "KRW",
                    "curBal": "1500000",
                    "ablBal": "1480000",
                    "list": [
                      {
                        "trDt": "%s",
                        "trTm": "093015",
                        "trRnd": "",
                        "wlbn": "",
                        "inAmt": "1000000",
                        "outAmt": "0",
                        "balance": "1500000",
                        "trBr": "",
                        "trTp": "입금",
                        "trDetail": "타행이체입금",
                        "memo": "",
                        "curCd": "KRW",
                        "recvAcctNo": "",
                        "recvAcctHolder": "",
                        "sendAcctNo": "",
                        "sendAcctHolder": "",
                        "vndrCode": "",
                        "corrCancelType": "",
                        "cmsCd": "",
                        "whtTax": "0"
                      },
                      {
                        "trDt": "%s",
                        "trTm": "141022",
                        "trRnd": "",
                        "wlbn": "",
                        "inAmt": "0",
                        "outAmt": "200000",
                        "balance": "1300000",
                        "trBr": "",
                        "trTp": "출금",
                        "trDetail": "이체출금",
                        "memo": "",
                        "curCd": "KRW",
                        "recvAcctNo": "",
                        "recvAcctHolder": "",
                        "sendAcctNo": "",
                        "sendAcctHolder": "",
                        "vndrCode": "",
                        "corrCancelType": "",
                        "cmsCd": "",
                        "whtTax": "0"
                      }
                    ],
                    "ctrList": []
                  }
                }
                """.formatted(stubCommonJson(), acctNo, toDate, toDate));
    }

    /** 자산거래내역조회 - 매수/매도 등 (in0104000540) */
    @Override
    public JsonNode fetchAssetTransactionHistory(
            HyphenCredential credential,
            String brokerName,
            String accountNumber,
            String fromDate,
            String toDate) {
        validateHyphenCredential(credential);
        requireKisBroker(brokerName);
        validateDateRange(fromDate, toDate);
        requireAccountNumber(accountNumber);
        log.info("[Hyphen STUB] KIS 자산거래내역조회 acctNo={}, {}~{}", accountNumber, fromDate, toDate);

        String acctNo = defaultString(accountNumber, KIS_ACCT_NO);

        return parseJson("""
                {
                  "common": %s,
                  "data": {
                    "acctNo": "%s",
                    "list": [
                      {
                        "trDt": "%s",
                        "trTm": "100530",
                        "itemCd": "QQQ",
                        "itemNm": "Invesco QQQ Trust",
                        "trDiv": "매수",
                        "trTp": "주식매수",
                        "trQt": "10",
                        "unitPrice": "70000",
                        "trAmt": "700000",
                        "balance": "707000",
                        "finalBalance": "707000",
                        "totalUnit": "10",
                        "totalSum": "700000",
                        "securitiesBal": "10",
                        "fees": "7000",
                        "tax": "0",
                        "interest": "0",
                        "curCd": "KRW",
                        "exYn": "N",
                        "prchsDt": "%s",
                        "exprtDt": "",
                        "depositBalance": "793000",
                        "clientNm": ""
                      },
                      {
                        "trDt": "%s",
                        "trTm": "153012",
                        "itemCd": "SOXX",
                        "itemNm": "iShares Semiconductor",
                        "trDiv": "매수",
                        "trTp": "주식매수",
                        "trQt": "5",
                        "unitPrice": "180000",
                        "trAmt": "900000",
                        "balance": "909000",
                        "finalBalance": "909000",
                        "totalUnit": "5",
                        "totalSum": "900000",
                        "securitiesBal": "5",
                        "fees": "9000",
                        "tax": "0",
                        "interest": "0",
                        "curCd": "KRW",
                        "exYn": "N",
                        "prchsDt": "%s",
                        "exprtDt": "",
                        "depositBalance": "591000",
                        "clientNm": ""
                      }
                    ]
                  }
                }
                """.formatted(stubCommonJson(), acctNo, toDate, toDate, toDate, toDate, toDate));
    }

    private String stubCommonJson() {
        return """
                {
                  "userTrNo": "%s",
                  "hyphenTrNo": "%s",
                  "errYn": "N",
                  "errCd": "",
                  "errMsg": ""
                }
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
    }

    private void requireKisBroker(String brokerName) {
        String code = getBankCode(brokerName);
        if (!"0243".equals(code)) {
            log.warn("[Hyphen STUB] KIS(0243) 기준 더미만 제공합니다. 요청 broker={}, bankCd={}", brokerName, code);
        }
    }

    private void validateHyphenCredential(HyphenCredential credential) {
        if (credential == null || isBlank(credential.userId()) || isBlank(credential.userPw())) {
            throw ApiException.badRequest("하이픈 연동 사용자 정보가 누락되었습니다.", "HYPHEN_LOGIN_INFO_MISSING");
        }
    }

    private void requireAccountNumber(String accountNumber) {
        if (isBlank(accountNumber)) {
            throw ApiException.badRequest("계좌번호가 필요합니다.", "MISSING_ACCOUNT_NUMBER");
        }
    }

    private void validateDateRange(String fromDate, String toDate) {
        if (!isValidDate(fromDate) || !isValidDate(toDate)) {
            throw ApiException.badRequest("조회 기간은 YYYYMMDD 형식이어야 합니다.", "INVALID_DATE_FORMAT");
        }
        if (fromDate.compareTo(toDate) > 0) {
            throw ApiException.badRequest("조회 시작일은 종료일보다 이후일 수 없습니다.", "INVALID_DATE_RANGE");
        }
    }

    private boolean isValidDate(String value) {
        return value != null && value.matches("\\d{8}");
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("스텁 JSON 파싱 실패: {}", e.getMessage());
            throw ApiException.internalServerError("스텁 응답 생성 실패", "HYPHEN_STUB_ERROR");
        }
    }

    private String getBankCode(String brokerName) {
        if (brokerName == null || brokerName.isBlank()) {
            return "0243";
        }
        String trimmed = brokerName.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            return trimmed;
        }
        return BROKER_BANK_CODES.getOrDefault(trimmed.toUpperCase(), "0243");
    }

    private static String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
