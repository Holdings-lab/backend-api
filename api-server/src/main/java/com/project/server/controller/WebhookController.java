package com.project.server.controller;

import com.project.server.dto.WebhookRequest;
import com.project.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<String> receiveEventWebhook(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secretHeader,
            @RequestBody WebhookRequest request) {
        
        if (!webhookSecret.equals(secretHeader)) {
            log.warn("Webhook 인증 실패. 올바르지 않은 Secret: {}", secretHeader);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Secret");
        }

        log.info("Webhook 정상 수신 완료: eventId={}", request.getEventId());
        
        notificationService.processPushNotification(request.getEventId(), request.getKeyword());

        return ResponseEntity.ok("Webhook received and async push processing started");
    }
}
