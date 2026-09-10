package com.project.server.service.newsroom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.service.llm.LlmApiService;
import com.project.server.service.llm.LlmPromptFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NewsroomLlmService {

    private static final Logger logger = LoggerFactory.getLogger(NewsroomLlmService.class);
    private static final int MAX_SOURCE_CHARS = 1200;

    private final LlmApiService llmApiService;
    private final ObjectMapper objectMapper;

    /**
     * 탭 카드용 headline/summary를 한국어로 번역·요약한다.
     * 실패 시 원문을 그대로 반환한다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, LocalizedText> localizeHoldings(List<LocalizedText> sources) {
        Map<String, LocalizedText> result = new LinkedHashMap<>();
        if (sources == null || sources.isEmpty()) {
            return result;
        }

        List<Map<String, Object>> payloadItems = new ArrayList<>();
        for (LocalizedText source : sources) {
            if (source == null || source.ticker() == null || source.ticker().isBlank()) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ticker", source.ticker());
            item.put("headline", truncate(source.headline()));
            item.put("summary", source.summary() == null || source.summary().isBlank()
                    ? null
                    : truncate(source.summary()));
            payloadItems.add(item);
            result.put(source.ticker().toUpperCase(Locale.ROOT), source);
        }
        if (payloadItems.isEmpty()) {
            return result;
        }

        try {
            Map<String, Object> llmResult = llmApiService.generateJson(
                    LlmPromptFactory.buildNewsroomLocalizeSystemPrompt(),
                    LlmPromptFactory.buildNewsroomLocalizeUserPrompt(payloadItems)
            );
            Object rawItems = llmResult.get("items");
            List<Map<String, Object>> items = objectMapper.convertValue(
                    rawItems == null ? List.of() : rawItems,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );
            for (Map<String, Object> item : items) {
                String ticker = stringValue(item.get("ticker"));
                if (ticker == null) {
                    continue;
                }
                String headline = stringValue(item.get("headline"));
                String summary = stringValue(item.get("summary"));
                LocalizedText original = result.get(ticker.toUpperCase(Locale.ROOT));
                if (original == null) {
                    continue;
                }
                result.put(
                        ticker.toUpperCase(Locale.ROOT),
                        new LocalizedText(
                                ticker.toUpperCase(Locale.ROOT),
                                headline != null ? headline : original.headline(),
                                summary != null ? summary : original.summary()
                        )
                );
            }
        } catch (Exception ex) {
            logger.warn("Newsroom holding localization failed; using source text. reason={}", ex.getMessage());
        }
        return result;
    }

    /**
     * 상세 브리핑 텍스트를 한국어로 번역·요약한다.
     * 실패 시 원문을 그대로 반환한다.
     */
    public DetailLocalizedText localizeDetail(DetailLocalizedText source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("headline", truncate(source.headline()));
        payload.put("summaryBody", truncate(source.summaryBody()));
        payload.put("findings", source.findings() == null ? List.of() : source.findings().stream()
                .map(this::truncate)
                .toList());
        payload.put("aiJudgement", truncate(source.aiJudgement()));

        try {
            Map<String, Object> llmResult = llmApiService.generateJson(
                    LlmPromptFactory.buildNewsroomDetailLocalizeSystemPrompt(),
                    LlmPromptFactory.buildNewsroomDetailLocalizeUserPrompt(payload)
            );
            String headline = stringValue(llmResult.get("headline"));
            String summaryBody = stringValue(llmResult.get("summaryBody"));
            String aiJudgement = stringValue(llmResult.get("aiJudgement"));
            List<String> findings = objectMapper.convertValue(
                    llmResult.getOrDefault("findings", List.of()),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            return new DetailLocalizedText(
                    headline != null ? headline : source.headline(),
                    summaryBody != null ? summaryBody : source.summaryBody(),
                    findings == null || findings.isEmpty() ? source.findings() : findings,
                    aiJudgement != null ? aiJudgement : source.aiJudgement()
            );
        } catch (Exception ex) {
            logger.warn("Newsroom detail localization failed; using source text. reason={}", ex.getMessage());
            return source;
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.length() <= MAX_SOURCE_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_SOURCE_CHARS).trim();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    public record LocalizedText(String ticker, String headline, String summary) {
    }

    public record DetailLocalizedText(
            String headline,
            String summaryBody,
            List<String> findings,
            String aiJudgement
    ) {
    }
}
