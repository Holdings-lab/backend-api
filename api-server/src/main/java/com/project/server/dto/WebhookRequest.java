package com.project.server.dto;

import lombok.Data;

@Data
public class WebhookRequest {
    private Long eventId;
    private String keyword;
}
