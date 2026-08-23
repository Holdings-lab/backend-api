package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class StockLogoService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiToken;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public StockLogoService(
            ObjectMapper objectMapper,
            @Value("${integration.finnhub.api-token:}") String apiToken
    ) {
        this.objectMapper = objectMapper;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * Finnhub /stock/profile2 에서 종목 로고 URL을 가져옴
     * 캐시에 있으면 재사용, 실패 시 null 반환 (빈 문자열도 캐시하여 재시도 방지)
     */
    public String getLogoUrl(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        String key = ticker.toUpperCase(Locale.ROOT);

        String cached = cache.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        if (apiToken == null || apiToken.isBlank()) {
            log.debug("Finnhub API 토큰이 설정되지 않아 로고 조회를 건너뜁니다: ticker={}", key);
            cache.put(key, "");
            return null;
        }

        try {
            String url = "https://finnhub.io/api/v1/stock/profile2?symbol="
                    + key + "&token=" + apiToken;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String logoUrl = root.has("logo") && !root.get("logo").isNull()
                        ? root.get("logo").asText()
                        : null;

                if (logoUrl != null && !logoUrl.isBlank()) {
                    cache.put(key, logoUrl);
                    return logoUrl;
                }
            }

            log.debug("Finnhub 로고 조회 실패 또는 로고 없음: ticker={}, status={}", key, response.statusCode());
        } catch (Exception ex) {
            log.warn("Finnhub 로고 조회 중 예외: ticker={}, message={}", key, ex.getMessage());
        }

        cache.put(key, "");
        return null;
    }

    /**
     * 여러 티커의 로고를 한 번에 프리로드
     * 이미 캐시된 티커는 건너뜀
     */
    public void preloadLogos(Iterable<String> tickers) {
        for (String ticker : tickers) {
            if (ticker != null && !ticker.isBlank()) {
                String key = ticker.toUpperCase(Locale.ROOT);
                if (!cache.containsKey(key)) {
                    getLogoUrl(key);
                }
            }
        }
    }
}
