package com.project.server.service.integration.kis;

import com.project.server.exception.ApiException;

final class KisAccountParser {

    private KisAccountParser() {
    }

    record Parts(String cano, String productCode) {
    }

    static Parts parse(String accountNumber, String productCode, String fallbackCano, String fallbackProduct) {
        String digits = accountNumber == null ? "" : accountNumber.replaceAll("[^0-9]", "");
        String product = blankToNull(productCode);
        String cano;

        if (digits.length() >= 10) {
            cano = digits.substring(0, 8);
            if (product == null) {
                product = digits.substring(8, 10);
            }
        } else if (digits.length() == 8) {
            cano = digits;
        } else if (!digits.isEmpty() && digits.length() > 8) {
            cano = digits.substring(0, 8);
        } else if (!digits.isEmpty()) {
            cano = digits;
        } else {
            cano = blankToNull(fallbackCano);
        }

        if (product == null) {
            product = defaultString(fallbackProduct, "01");
        }
        return new Parts(cano, product);
    }

    static String storedNumber(String cano, String productCode) {
        return cano + productCode;
    }

    static String display(String cano, String productCode) {
        return cano + "-" + productCode;
    }

    static void requireCano(Parts parts) {
        if (parts == null || isBlank(parts.cano()) || parts.cano().length() < 8) {
            throw ApiException.badRequest(
                    "계좌번호가 필요합니다. accountNumber 또는 KIS_MOCK_CANO를 확인하세요.",
                    "MISSING_KIS_ACCOUNT");
        }
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
