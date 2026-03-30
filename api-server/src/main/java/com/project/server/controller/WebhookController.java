package com.project.server.controller;

import com.project.server.dto.ActionDto;
import com.project.server.dto.WebhookRequest;
import com.project.server.exception.ApiException;
import com.project.server.service.integration.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/internal/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final NotificationService notificationService;

    @Value("${webhook.secret}")
    private String webhookSecret;

    @PostMapping("/event")
    public ResponseEntity<ActionDto.ActionResponse> receiveEventWebhook(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secretHeader,
            @RequestBody WebhookRequest request) {

        if (!webhookSecret.equals(secretHeader)) {
            log.warn("Webhook 인증 실패. 올바르지 않은 Secret: {}", secretHeader);
            throw ApiException.unauthorized("Webhook 인증에 실패했습니다.", "WEBHOOK_UNAUTHORIZED");
        }

        Long eventId = request.getEventId();
        String keyword = request.getKeyword();

        if (eventId == null) {
            throw ApiException.badRequest("eventId는 필수입니다.", "WEBHOOK_EVENT_ID_REQUIRED");
        }

        if (keyword == null || keyword.isBlank()) {
            keyword = "unknown";
        }

        log.info("Webhook 정상 수신 완료: eventId={}", eventId);
        notificationService.processPushNotification(eventId, keyword);

        return ResponseEntity.ok(ActionDto.ActionResponse.builder()
            .message("Webhook 수신 후 비동기 푸시 처리가 시작되었습니다.")
            .build());
    }
}
