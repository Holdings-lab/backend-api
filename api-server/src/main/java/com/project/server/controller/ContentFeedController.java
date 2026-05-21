package com.project.server.controller;

import com.project.server.dto.PolicyFeedDto;
import com.project.server.service.integration.PolicyFeedProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/feeds/policy")
@RequiredArgsConstructor
public class ContentFeedController {

    private final PolicyFeedProxyService policyFeedProxyService;

    @GetMapping
    public ResponseEntity<PolicyFeedDto.PolicyFeedResponse> getPolicyFeed(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "dateFrom", required = false) String dateFrom,
            @RequestParam(name = "dateTo", required = false) String dateTo) {
        return ResponseEntity.ok(policyFeedProxyService.getPolicyFeed(limit, category, dateFrom, dateTo));
    }

    @GetMapping("/meta")
    public ResponseEntity<java.util.Map<String, String>> getPolicyFeedMeta() {
        return ResponseEntity.ok(policyFeedProxyService.getMeta());
    }

    @GetMapping("/source")
    public ResponseEntity<PolicyFeedDto.Source> getPolicyFeedSource() {
        return ResponseEntity.ok(policyFeedProxyService.getSource());
    }

    @GetMapping("/summary")
    public ResponseEntity<PolicyFeedDto.Summary> getPolicyFeedSummary() {
        return ResponseEntity.ok(policyFeedProxyService.getSummary());
    }

    @GetMapping("/model")
    public ResponseEntity<PolicyFeedDto.Model> getPolicyFeedModel() {
        return ResponseEntity.ok(policyFeedProxyService.getModel());
    }

    @GetMapping("/filters")
    public ResponseEntity<PolicyFeedDto.Filters> getPolicyFeedFilters() {
        return ResponseEntity.ok(policyFeedProxyService.getFilters());
    }

    @GetMapping("/cards")
    public ResponseEntity<java.util.List<PolicyFeedDto.Card>> getPolicyFeedCards(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "dateFrom", required = false) String dateFrom,
            @RequestParam(name = "dateTo", required = false) String dateTo) {
        return ResponseEntity.ok(policyFeedProxyService.getCards(limit, category, dateFrom, dateTo));
    }

    @GetMapping("/stats")
    public ResponseEntity<PolicyFeedDto.PolicyFeedStatsResponse> getPolicyFeedStats(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "dateFrom", required = false) String dateFrom,
            @RequestParam(name = "dateTo", required = false) String dateTo) {
        return ResponseEntity.ok(policyFeedProxyService.getPolicyFeedStats(limit, category, dateFrom, dateTo));
    }
}