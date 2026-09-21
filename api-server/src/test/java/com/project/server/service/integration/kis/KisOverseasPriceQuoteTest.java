package com.project.server.service.integration.kis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class KisOverseasPriceQuoteTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesRateFromOverseasPriceOutput() {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("last", "512.30");
        output.put("base", "510.00");
        output.put("rate", "0.45");

        KisApiClient.OverseasPriceQuote quote =
                KisFieldMapper.toOverseasPriceQuote("NAS", "QQQ", output);

        assertNotNull(quote);
        assertEquals(new BigDecimal("0.45"), quote.rate());
        assertEquals(new BigDecimal("512.30"), quote.last());
    }

    @Test
    void returnsNullWhenRateMissing() {
        ObjectNode output = objectMapper.createObjectNode();
        output.put("last", "100");

        assertNull(KisFieldMapper.toOverseasPriceQuote("NAS", "QQQ", output));
    }

    @Test
    void totalAssetImpactMatchesWeightFormula() {
        BigDecimal dailyChangePct = new BigDecimal("1.5");
        BigDecimal weightPct = new BigDecimal("20.0");
        BigDecimal impact = dailyChangePct
                .multiply(weightPct)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("0.30"), impact);
    }
}
