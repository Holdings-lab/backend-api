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
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "dateFrom", required = false) String dateFrom,
            @RequestParam(name = "dateTo", required = false) String dateTo) {
        return ResponseEntity.ok(policyFeedProxyService.getPolicyFeed(userId, limit, category, dateFrom, dateTo));
    }

    @GetMapping("/meta")
    public ResponseEntity<java.util.Map<String, String>> getPolicyFeedMeta(
            @RequestParam(name = "userId", required = false) Long userId) {
        return ResponseEntity.ok(policyFeedProxyService.getMeta(userId));
    }

    @GetMapping("/source")
    public ResponseEntity<PolicyFeedDto.Source> getPolicyFeedSource(
            @RequestParam(name = "userId", required = false) Long userId) {
        return ResponseEntity.ok(policyFeedProxyService.getSource(userId));
    }

    @GetMapping("/summary")
    public ResponseEntity<PolicyFeedDto.Summary> getPolicyFeedSummary(
            @RequestParam(name = "userId", required = false) Long userId) {
        return ResponseEntity.ok(policyFeedProxyService.getSummary(userId));
    }

    @GetMapping("/model")
    public ResponseEntity<PolicyFeedDto.Model> getPolicyFeedModel(
            @RequestParam(name = "userId", required = false) Long userId) {
        return ResponseEntity.ok(policyFeedProxyService.getModel(userId));
    }

    @GetMapping("/filters")
    public ResponseEntity<PolicyFeedDto.Filters> getPolicyFeedFilters(
            @RequestParam(name = "userId", required = false) Long userId) {
        return ResponseEntity.ok(policyFeedProxyService.getFilters(userId));
    }

    @GetMapping("/cards")
    public ResponseEntity<java.util.List<PolicyFeedDto.Card>> getPolicyFeedCards(
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "dateFrom", required = false) String dateFrom,
            @RequestParam(name = "dateTo", required = false) String dateTo) {
        return ResponseEntity.ok(policyFeedProxyService.getCards(userId, limit, category, dateFrom, dateTo));
    }

    @GetMapping("/stats")
    public ResponseEntity<PolicyFeedDto.PolicyFeedStatsResponse> getPolicyFeedStats(
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "dateFrom", required = false) String dateFrom,
            @RequestParam(name = "dateTo", required = false) String dateTo) {
        return ResponseEntity.ok(policyFeedProxyService.getPolicyFeedStats(userId, limit, category, dateFrom, dateTo));
    }
}