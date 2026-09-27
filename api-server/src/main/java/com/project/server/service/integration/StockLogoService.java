package com.project.server.service.integration;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class StockLogoService {

    private static final Pattern TICKER = Pattern.compile("[A-Z0-9][A-Z0-9.\\-]{0,15}");
    private static final String LOGO_URL = "https://eodhd.com/img/logos/US/%s.png";

    /**
     * EODHD 공개 PNG 로고 주소. 네트워크 호출 없이 티커만 넣는다.
     */
    public String getLogoUrl(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        String key = ticker.trim().toUpperCase(Locale.ROOT);
        if (!TICKER.matcher(key).matches()) {
            return null;
        }
        return LOGO_URL.formatted(key);
    }

    public void preloadLogos(Iterable<String> tickers) {
        if (tickers == null) {
            return;
        }
        for (String ticker : tickers) {
            getLogoUrl(ticker);
        }
    }
}
