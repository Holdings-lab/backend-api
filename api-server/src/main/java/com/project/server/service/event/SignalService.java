package com.project.server.service.event;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SignalService {
    public List<Map<String, Object>> collectPolicyNewsRaw() { return Collections.singletonList(Map.of("rawTitle", "미 연준 50bp 금리 인하 확정")); }
    public double calculateAssetSensitivity(List<String> assets) { return 1.5; }
    public Map<String, Object> calculateSignalMetrics(String event) { return new HashMap<>(); }
    public Map<String, Object> rankSignalsForUser(List<String> assets, List<Map<String, Object>> rawSignals) {
        return Map.of("primarySignalId", "EVT-001", "rankScore", 95.5);
    }
    public String formatSignalTitle(String raw) { return raw; }
    public double calculateExposurePercent(List<String> assets, double sensitivity) { return 42.5; }
    public Map<String, Object> generateActionPlan(Map<String, Object> metrics) {
        return Map.of("action", "방어 비중 점검", "description", "변동성이 높으므로 포트폴리오 리밸런싱을 고려하세요.");
    }
    public List<String> generateReasonCandidates(String event) { return Arrays.asList("이유1", "이유2"); }
    public List<Map<String, Object>> getSecondarySignals(List<Map<String, Object>> rawSignals) {
        return Collections.singletonList(Map.of("id", "EVT-002", "title", "엔비디아 실적 발표", "exposure", 15.0, "decision", "기다리기"));
    }
}
