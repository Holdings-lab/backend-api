package com.project.server.service.integration;

import com.project.server.service.event.EventScheduleService;
import com.project.server.service.home.FeaturedEventStateService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPipelineTriggerService {

    private final EventScheduleService eventScheduleService;
    private final FeaturedEventStateService featuredEventStateService;

    public void triggerAndUpdateFeatured(Long userId) {
        triggerAiEngine();
        EventScheduleService.EventSchedule current = eventScheduleService.getCurrentEvent(userId);
        featuredEventStateService.setFeatured(
                userId,
                current.title(),
                current.title() + " · 발표 전",
                "D-0 00:31",
                List.of("미국", "예측높음")
        );
    }

    private void triggerAiEngine() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8000/ai/test-trigger"))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        try {
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // 개발 단계에서는 AI 엔진이 내려가 있어도 더미 시나리오를 계속 진행한다.
        }
    }
}
