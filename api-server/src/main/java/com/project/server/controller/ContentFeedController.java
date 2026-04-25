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

    @PostMapping("/policy-feed/meta")
    public ResponseEntity<java.util.Map<String, String>> getPolicyFeedMeta(
            @RequestBody(required = false) PolicyFeedDto.PolicyFeedRequest request
    ) {
        return ResponseEntity.ok(policyFeedProxyService.getMeta(request));
    }

    @PostMapping("/policy-feed/source")
    public ResponseEntity<PolicyFeedDto.Source> getPolicyFeedSource(
            @RequestBody(required = false) PolicyFeedDto.PolicyFeedRequest request
    ) {
        return ResponseEntity.ok(policyFeedProxyService.getSource(request));
    }

    @PostMapping("/policy-feed/summary")
    public ResponseEntity<PolicyFeedDto.Summary> getPolicyFeedSummary(
            @RequestBody(required = false) PolicyFeedDto.PolicyFeedRequest request
    ) {
        return ResponseEntity.ok(policyFeedProxyService.getSummary(request));
    }

    @PostMapping("/policy-feed/model")
    public ResponseEntity<PolicyFeedDto.Model> getPolicyFeedModel(
            @RequestBody(required = false) PolicyFeedDto.PolicyFeedRequest request
    ) {
        return ResponseEntity.ok(policyFeedProxyService.getModel(request));
    }

    @PostMapping("/policy-feed/filters")
    public ResponseEntity<PolicyFeedDto.Filters> getPolicyFeedFilters(
            @RequestBody(required = false) PolicyFeedDto.PolicyFeedRequest request
    ) {
        return ResponseEntity.ok(policyFeedProxyService.getFilters(request));
    }

    @PostMapping("/policy-feed/cards")
    public ResponseEntity<java.util.List<PolicyFeedDto.Card>> getPolicyFeedCards(
            @RequestBody(required = false) PolicyFeedDto.PolicyFeedRequest request
    ) {
        return ResponseEntity.ok(policyFeedProxyService.getCards(request));
    }

    @PostMapping("/policy-feed/featured-card")
    public ResponseEntity<PolicyFeedDto.Card> getPolicyFeedFeaturedCard(
            @RequestBody(required = false) PolicyFeedDto.PolicyFeedRequest request
    ) {
        return ResponseEntity.ok(policyFeedProxyService.getFeaturedCard(request));
    }
}