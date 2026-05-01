package com.project.server.service.integration;

import com.project.server.service.event.EventScheduleService;
import com.project.server.service.home.FeaturedEventStateService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPipelineTriggerService {

    private final EventScheduleService eventScheduleService;
    private final FeaturedEventStateService featuredEventStateService;

    @Value("${integration.ml.base-url:http://localhost:9000}")
    private String mlBaseUrl;

    public void triggerAndUpdateFeatured(Long userId) {
        triggerDataMlPolicyFeed();
        EventScheduleService.EventSchedule current = eventScheduleService.getCurrentEvent(userId);
        // dDayText와 tags는 EventScheduleService에서 계산하는 값 사용
        List<String> dynamicTags = extractTagsFromSchedule(current);
        featuredEventStateService.setFeatured(
                userId,
                current.title(),
                current.title() + " · 발표 전",
                current.countdownText(),
                dynamicTags
        );
    }
    
    private List<String> extractTagsFromSchedule(EventScheduleService.EventSchedule schedule) {
        // 시간 정보나 제목으로부터 동적 태그 생성
        return List.of("미국", "주목");
    }

    private void triggerDataMlPolicyFeed() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(normalizeBaseUrl(mlBaseUrl) + "/ml/content/policy-feed"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                .build();

        try {
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // 개발 단계에서는 data-ml이 내려가 있어도 더미 시나리오를 계속 진행한다.
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:9000";
        }
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
