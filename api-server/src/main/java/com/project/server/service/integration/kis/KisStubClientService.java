package com.project.server.service.integration.kis;

import com.project.server.config.KisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnExpression("'stub'.equalsIgnoreCase('${kis.api.mode:stub}')")
public class KisStubClientService implements KisApiClient {

    private final KisProperties kisProperties;

    @Override
    public KisBalanceSnapshot fetchBalance(KisCredential credential) {
        String cano = credential != null && notBlank(credential.cano())
                ? credential.cano()
                : defaultCano();
        String productCode = credential != null && notBlank(credential.accountProductCode())
                ? credential.accountProductCode()
                : defaultProduct();
        log.info("[KIS STUB] inquire-balance CANO={} ACNT_PRDT_CD={}", cano, productCode);

        KisPosition samsung = new KisPosition(
                "005930",
                "삼성전자",
                "STOCK",
                "005930",
                new BigDecimal("10"),
                new BigDecimal("70000"),
                new BigDecimal("75000"),
                new BigDecimal("750000"),
                new BigDecimal("700000"),
                new BigDecimal("50000"),
                new BigDecimal("7.14"),
                "KRW",
                "N");
        KisPosition sk = new KisPosition(
                "000660",
                "SK하이닉스",
                "STOCK",
                "000660",
                new BigDecimal("5"),
                new BigDecimal("180000"),
                new BigDecimal("190000"),
                new BigDecimal("950000"),
                new BigDecimal("900000"),
                new BigDecimal("50000"),
                new BigDecimal("5.56"),
                "KRW",
                "N");

        return new KisBalanceSnapshot(
                cano,
                productCode,
                KisAccountParser.display(cano, productCode),
                new BigDecimal("1480000"),
                new BigDecimal("3180000"),
                new BigDecimal("1700000"),
                new BigDecimal("1600000"),
                new BigDecimal("100000"),
                new BigDecimal("6.25"),
                List.of(samsung, sk));
    }

    private String defaultCano() {
        String cano = kisProperties.getMock().getCano();
        return notBlank(cano) ? cano.trim() : "43123456";
    }

    private String defaultProduct() {
        String product = kisProperties.getMock().getAcntPrdtCd();
        return notBlank(product) ? product.trim() : "01";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
