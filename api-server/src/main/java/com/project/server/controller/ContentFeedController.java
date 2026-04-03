package com.project.server.controller;

import com.project.server.dto.PolicyFeedDto;
import com.project.server.service.integration.PolicyFeedProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentFeedController {

    private final PolicyFeedProxyService policyFeedProxyService;

    @PostMapping("/policy-feed")
    public ResponseEntity<PolicyFeedDto.PolicyFeedResponse> getPolicyFeed(
            @RequestBody(required = false) PolicyFeedDto.PolicyFeedRequest request
    ) {
        return ResponseEntity.ok(policyFeedProxyService.getPolicyFeed(request));
    }
}