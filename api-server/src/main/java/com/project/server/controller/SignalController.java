package com.project.server.controller;

import com.project.server.service.event.SignalService;
import com.project.server.service.event.SignalDetailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/signals")
public class SignalController {
    private final SignalService signalService;
    private final SignalDetailService signalDetailService;

    public SignalController(SignalService signalService, SignalDetailService signalDetailService) {
        this.signalService = signalService;
        this.signalDetailService = signalDetailService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSignalDetail(@PathVariable String id) {
        List<String> mockAssets = Arrays.asList("QQQ", "AAPL");
        double sensitivity = signalService.calculateAssetSensitivity(mockAssets);
        List<String> reasons = signalService.generateReasonCandidates(id);
        
        Map<String, Object> decisionBadge = signalDetailService.formatDecisionBadge(new HashMap<>());
        Map<String, Object> impactZone = signalDetailService.generateImpactZoneDesc(mockAssets, sensitivity);
        String keyReason = signalDetailService.summarizeKeyReason(reasons);
        Map<String, Object> keyNumbers = signalDetailService.extractKeyNumbers("text");
        String revisitTime = signalDetailService.formatRevisitTime("time");
        String weakeningCondition = signalDetailService.extractWeakeningConditions("cond");
        String behaviorTips = signalDetailService.generateBehaviorTips("label");
        List<Map<String, Object>> impactPath = signalDetailService.extractImpactPath("path");
        List<String> refinedEvidence = signalDetailService.refineKeyEvidence(Arrays.asList("ev1"));
        Map<String, Object> counterArguments = signalDetailService.generateCounterArguments(id);
        List<String> invalidationRules = signalDetailService.formatInvalidationRules("rules");
        List<Map<String, Object>> policyCheckpoints = signalDetailService.classifyPolicyCheckpoints("text");
        List<Map<String, Object>> marketIndicators = signalDetailService.mapMarketIndicators(new HashMap<>());
        String notifyRules = signalDetailService.generateNotifyRules("time");
        Map<String, Object> systemStatus = signalDetailService.getSystemStatus();
        String disclaimer = signalDetailService.getStaticDisclaimer();

        Map<String, Object> response = new HashMap<>();
        response.put("id", id);
        response.put("fastInterpretation", Map.of("badge", decisionBadge, "impactZone", impactZone, "keyReason", keyReason, "keyNumbers", keyNumbers, "revisitTime", revisitTime, "weakeningCondition", weakeningCondition, "behaviorTips", behaviorTips));
        response.put("detailedEvidence", Map.of("impactPath", impactPath, "refinedEvidence", refinedEvidence, "counterArguments", counterArguments, "invalidationRules", invalidationRules));
        response.put("checkpointMetadata", Map.of("policyCheckpoints", policyCheckpoints, "marketIndicators", marketIndicators, "notifyRules", notifyRules, "systemStatus", systemStatus, "disclaimer", disclaimer));
        return ResponseEntity.ok(response);
    }
}
