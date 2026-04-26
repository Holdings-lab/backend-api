package com.project.server.service.event;

import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class SignalDetailService {
    public Map<String, Object> formatDecisionBadge(Map<String, Object> data) {
        return Map.of("label", "방어 비중 점검", "color", "ORANGE", "icon", "SHIELD");
    }
    public Map<String, Object> generateImpactZoneDesc(List<String> assets, double sensitivity) {
        return Map.of("exposurePercent", 42.5, "description", "보유하신 QQQ 등 기술주 위주로 영향이 예상됩니다.");
    }
    public String summarizeKeyReason(List<String> reasons) { return "금리 인하로 인한 기술주 밸류에이션 부담 완화"; }
    public Map<String, Object> extractKeyNumbers(String text) { return Map.of("keyFigure", "50bp", "context", "기준 금리 인하폭"); }
    public String formatRevisitTime(String time) { return "내일 아침 8시 시장 개장 전 다시 확인하세요."; }
    public String extractWeakeningConditions(String condition) { return "만약 예상보다 인플레이션 지표가 높게 나온다면 상승 동력이 약해질 수 있습니다."; }
    public String generateBehaviorTips(String label) { return "현금 비중을 10% 이상 확보하는 것을 권장합니다."; }
    public List<Map<String, Object>> extractImpactPath(String text) { return Collections.singletonList(Map.of("step", "정책", "desc", "금리 인하", "icon", "BANK")); }
    public List<String> refineKeyEvidence(List<String> evidence) { return Collections.singletonList("과거 3차례 금리 인하 사이클에서 QQQ 평균 15% 상승"); }
    public Map<String, Object> generateCounterArguments(String id) { return Map.of("title", "반대 시각", "desc", "차익실현 매물이 출회될 수 있습니다."); }
    public List<String> formatInvalidationRules(String rules) { return Collections.singletonList("고용 지표 악화 시 침체 우려로 전환"); }
    public List<Map<String, Object>> classifyPolicyCheckpoints(String text) { return Collections.singletonList(Map.of("type", "POLICY", "target", "파월 의장 발언 강도", "baseline", "비둘기파적 기조 유지 여부")); }
    public List<Map<String, Object>> mapMarketIndicators(Map<String, Object> data) { return Collections.singletonList(Map.of("indicator", "미국 10년물 국채 금리", "threshold", "3.8% 하회 여부")); }
    public String generateNotifyRules(String time) { return "이벤트 발생 1시간 전 및 지표 발표 직후 알림을 드립니다."; }
    public Map<String, Object> getSystemStatus() { return Map.of("crawledAt", "2026-04-26T10:00:00Z", "modelStatus", "VERIFIED"); }
    public String getStaticDisclaimer() { return "본 정보는 투자 참고용이며, 실제 투자 결과에 대한 책임은 투자자 본인에게 있습니다."; }
}
