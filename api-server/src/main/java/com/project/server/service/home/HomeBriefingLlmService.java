package com.project.server.service.home;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.service.llm.LlmApiService;
import com.project.server.service.llm.LlmPromptFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HomeBriefingLlmService {

    private static final Logger logger = LoggerFactory.getLogger(HomeBriefingLlmService.class);

    private final LlmApiService llmApiService;
    private final ObjectMapper objectMapper;

    public Narrative generateNarrative(Map<String, Object> snapshot) {
        try {
            Map<String, Object> result = llmApiService.generateJson(
                    LlmPromptFactory.buildHomeBriefingSystemPrompt(),
                    LlmPromptFactory.buildHomeBriefingUserPrompt(snapshot)
            );

            String headline = safeText(result.get("headline"), "오늘의 정책 흐름 브리핑");
            List<String> paragraphs = objectMapper.convertValue(
                    result.getOrDefault("paragraphs", List.of()),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            String pushTitle = safeText(result.get("pushTitle"), headline);
            String pushBody = safeText(result.get("pushBody"), headline);
            String briefingTone = safeText(result.get("briefingTone"), "neutral");

            return new Narrative(llmApiService.getProviderName(), llmApiService.getModelName(), headline, paragraphs, pushTitle, pushBody, briefingTone, false);
        } catch (Exception exception) {
            logger.warn("Home briefing LLM generation failed; fallback will be used. reason={}", exception.getMessage());
            return buildFallbackNarrative(snapshot);
        }
    }

    private Narrative buildFallbackNarrative(Map<String, Object> snapshot) {
        String title = "오늘의 정책 흐름 브리핑";
        String summary = safeText(snapshot.get("featuredSummary"), "정책 이벤트와 포트폴리오 반응을 함께 점검할 필요가 있습니다.");
        List<String> paragraphs = List.of(
                summary,
                safeText(snapshot.get("riskSummary"), "현재 신호는 과도한 확언 없이 재확인이 필요한 구간으로 해석됩니다."),
                "내 자산 영향과 최신 정책 이벤트를 함께 비교해보는 구간입니다."
        );
        return new Narrative(llmApiService.getProviderName(), llmApiService.getModelName(), title, paragraphs, title, summary, "neutral", true);
    }

    private String safeText(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    public record Narrative(
            String providerName,
            String modelName,
            String headline,
            List<String> paragraphs,
            String pushTitle,
            String pushBody,
            String tone,
            boolean fallback
    ) {
        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("headline", headline);
            payload.put("paragraphs", paragraphs);
            payload.put("pushTitle", pushTitle);
            payload.put("pushBody", pushBody);
            payload.put("briefingTone", tone);
            payload.put("fallback", fallback);
            return payload;
        }
    }
}
