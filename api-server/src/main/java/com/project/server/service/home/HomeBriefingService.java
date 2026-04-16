package com.project.server.service.home;

import com.project.server.domain.PolicyEventEntity;
import com.project.server.domain.UserEntity;
import com.project.server.domain.UserWatchAssetEntity;
import com.project.server.dto.HomeBriefingDto;
import com.project.server.dto.WatchAssetDto;
import com.project.server.repository.PolicyEventJpaRepository;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.UserWatchAssetRepository;
import com.project.server.service.auth.WatchAssetSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HomeBriefingService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final double DEFAULT_TOTAL_ASSET = 100_000_000.0;

    private final UserJpaRepository userJpaRepository;
    private final UserWatchAssetRepository userWatchAssetRepository;
    private final PolicyEventJpaRepository policyEventJpaRepository;
    private final WatchAssetSelectionService watchAssetSelectionService;

    public HomeBriefingDto.BriefingResponse getBriefing(Long userId) {
        String userName = resolveUserName(userId);
        String profileInitial = resolveProfileInitial(userName);

        List<AssetPosition> positions = loadAssetPositions(userId);
        List<PolicyEventEntity> events = deduplicateByNormalizedTitle(
                policyEventJpaRepository.findTop20ByOrderByCreatedAtDesc());
        if (events.isEmpty()) {
            events = List.of(PolicyEventEntity.builder()
                    .id(0L)
                    .title("정책 이벤트 데이터 대기")
                    .keyword("macro")
                    .impactScore(50.0)
                    .analysisSummary("수집된 정책 이벤트가 아직 없어 기본 시그널을 표시합니다.")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        List<ScoredEvent> ranked = events.stream()
                .map(event -> scoreEvent(event, positions))
                .sorted(Comparator.comparingDouble(ScoredEvent::priority).reversed())
                .toList();

        ScoredEvent featured = ranked.get(0);
        List<ScoredEvent> secondary = ranked.stream().skip(1).limit(2).toList();

        int aggregateRisk = calculateAggregateRisk(ranked, positions);
        String riskLabel = aggregateRisk >= 75 ? "위험" : aggregateRisk >= 50 ? "주의" : "양호";

        Map<String, Integer> themeExposures = calculateThemeExposures(positions);
        double weightedReturn = positions.stream().mapToDouble(position -> position.weight() * position.changePercent())
                .sum();

        HomeBriefingDto.QuickInterpretation quickInterpretation = buildQuickInterpretation(featured);
        HomeBriefingDto.DetailTabs detailTabs = buildDetailTabs(featured);
        HomeBriefingDto.CheckpointTab checkpointTab = buildCheckpointTab(featured);

        return HomeBriefingDto.BriefingResponse.builder()
                .homeHeader(HomeBriefingDto.HomeHeader.builder()
                        .greeting("안녕하세요, " + userName + "님")
                        .userName(userName)
                        .profileInitial(profileInitial)
                        .build())
                .featuredCard(HomeBriefingDto.FeaturedSignalCard.builder()
                        .signalTitle(featured.displayTitle())
                        .myAssetExposurePercent(featured.exposurePercent())
                        .recommendedAction(
                                mapActionToAppTerm(featured.direction(), featured.volatility(), featured.confidence()))
                        .judgement(mapJudgement(featured.direction(), featured.confidence()))
                        .upsideProbability(featured.upsideProbability())
                        .downsideProbability(featured.downsideProbability())
                        .volatility(featured.volatility())
                        .confidence(featured.confidence())
                        .coreReason(featured.oneLineReason())
                        .build())
                .portfolioCard(HomeBriefingDto.PortfolioCard.builder()
                        .totalAsset(String.format(Locale.US, "%,.0f원", DEFAULT_TOTAL_ASSET))
                        .returnRate(String.format(Locale.US, "%+.2f%%", weightedReturn))
                        .currentRiskLabel(riskLabel)
                        .currentRiskSummary(buildRiskSummary(riskLabel, aggregateRisk, featured.confidence()))
                        .themeExposureBars(List.of(
                                HomeBriefingDto.ThemeExposureBar.builder().theme("금리")
                                        .exposurePercent(themeExposures.getOrDefault("금리", 0)).build(),
                                HomeBriefingDto.ThemeExposureBar.builder().theme("반도체")
                                        .exposurePercent(themeExposures.getOrDefault("반도체", 0)).build(),
                                HomeBriefingDto.ThemeExposureBar.builder().theme("달러")
                                        .exposurePercent(themeExposures.getOrDefault("달러", 0)).build()))
                        .build())
                .secondarySignals(secondary.stream().map(event -> HomeBriefingDto.SecondarySignalItem.builder()
                        .title(event.displayTitle())
                        .shortJudgement(event.shortJudgement())
                        .exposurePercent(event.exposurePercent())
                        .oneLineReason(event.oneLineReason())
                        .build()).toList())
                .quickInterpretation(quickInterpretation)
                .detailTabs(detailTabs)
                .checkpointTab(checkpointTab)
                .disclaimer("본 정보는 투자 자문이 아니며, 학습 및 정보 제공 목적의 참고 자료입니다.")
                .build();
    }

    private List<PolicyEventEntity> deduplicateByNormalizedTitle(List<PolicyEventEntity> events) {
        Map<String, PolicyEventEntity> deduped = new LinkedHashMap<>();
        for (PolicyEventEntity event : events) {
            String key = normalizeTitle(event.getTitle());
            PolicyEventEntity existing = deduped.get(key);
            if (existing == null || safeImpact(event) > safeImpact(existing)) {
                deduped.put(key, event);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private String normalizeTitle(String title) {
        if (title == null) {
            return "unknown";
        }
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("[\\[\\]{}()]+", "")
                .trim();
    }

    private ScoredEvent scoreEvent(PolicyEventEntity event, List<AssetPosition> positions) {
        double impact = safeImpact(event);
        Theme theme = classifyTheme(event);
        int exposure = clamp((int) Math.round(positions.stream()
                .mapToDouble(position -> position.weight() * sensitivity(position.assetName(), theme))
                .sum()));

        int volatility = clamp((int) Math.round(impact * 0.9 + (theme == Theme.DOLLAR ? 10 : 0)));
        int confidence = clamp((int) Math.round(55 + impact * 0.35));
        int upside = clamp((int) Math.round((impact + exposure) / 2.0));
        int downside = clamp(100 - upside);
        String direction = upside >= downside ? "상승" : "하락";

        double priority = exposure * 0.45 + volatility * 0.25 + confidence * 0.30;
        String reason = summarizeReason(event.getAnalysisSummary(), theme, exposure, confidence);
        return new ScoredEvent(
                event,
                sanitizeTitle(event.getTitle(), theme),
                exposure,
                volatility,
                confidence,
                upside,
                downside,
                direction,
                priority,
                mapShortJudgement(direction, confidence),
                reason,
                theme);
    }

    private String sanitizeTitle(String title, Theme theme) {
        if (title == null || title.isBlank()) {
            return theme.koreanName() + " 관련 정책 이벤트";
        }
        return title.length() > 46 ? title.substring(0, 46) + "…" : title;
    }

    private String summarizeReason(String summary, Theme theme, int exposure, int confidence) {
        if (summary != null && !summary.isBlank()) {
            String trimmed = summary.trim();
            return trimmed.length() > 88 ? trimmed.substring(0, 88) + "…" : trimmed;
        }
        return theme.koreanName() + " 이슈가 내 자산 노출 " + exposure + "% 구간과 겹치며 신뢰도 " + confidence + "%로 산출되었습니다.";
    }

    private int calculateAggregateRisk(List<ScoredEvent> ranked, List<AssetPosition> positions) {
        if (ranked.isEmpty()) {
            return 30;
        }
        double topSignalWeight = ranked.stream().limit(3).mapToDouble(ScoredEvent::priority).sum()
                / Math.max(1, Math.min(3, ranked.size()));
        double exposureWeight = positions.stream()
                .mapToDouble(position -> Math.abs(position.changePercent()) * position.weight()).sum() * 14;
        return clamp((int) Math.round(topSignalWeight * 0.5 + exposureWeight * 0.5));
    }

    private Map<String, Integer> calculateThemeExposures(List<AssetPosition> positions) {
        Map<String, Integer> result = new HashMap<>();
        for (String theme : List.of("금리", "반도체", "달러")) {
            Theme mapped = switch (theme) {
                case "금리" -> Theme.RATE;
                case "반도체" -> Theme.SEMICONDUCTOR;
                default -> Theme.DOLLAR;
            };
            int exposure = clamp((int) Math.round(positions.stream()
                    .mapToDouble(position -> position.weight() * sensitivity(position.assetName(), mapped))
                    .sum()));
            result.put(theme, exposure);
        }
        return result;
    }

    private HomeBriefingDto.QuickInterpretation buildQuickInterpretation(ScoredEvent featured) {
        String badgeText = mapJudgement(featured.direction(), featured.confidence());
        String badgeColor = featured.direction().equals("상승") ? "red" : "blue";

        return HomeBriefingDto.QuickInterpretation.builder()
                .judgementBadge(HomeBriefingDto.JudgementBadge.builder()
                        .text(badgeText)
                        .color(badgeColor)
                        .displayType("badge")
                        .build())
                .myAssetImpact("내 자산 영향권 " + featured.exposurePercent() + "% · " + featured.shortJudgement())
                .coreReason(featured.oneLineReason())
                .keyNumbers(List.of(
                        HomeBriefingDto.KeyNumberItem.builder().label("상승 가능성")
                                .baseline(featured.upsideProbability() + "%").description("이벤트 및 민감도 합산 추정치").build(),
                        HomeBriefingDto.KeyNumberItem.builder().label("하락 가능성")
                                .baseline(featured.downsideProbability() + "%").description("반대 시나리오 확률").build(),
                        HomeBriefingDto.KeyNumberItem.builder().label("변동성").baseline(featured.volatility() + "pt")
                                .description("정책 영향 강도 기반 위험 추정").build()))
                .revisitTime("발표 후 30분 내 재확인, 이후 장 마감 전 한 번 더 점검 권장")
                .weakenCondition("핵심 수치가 컨센서스와 반대로 나오거나 시장 반응 강도가 절반 미만이면 판단이 약화됩니다.")
                .tip(mapActionToAppTerm(featured.direction(), featured.volatility(), featured.confidence()))
                .build();
    }

    private HomeBriefingDto.DetailTabs buildDetailTabs(ScoredEvent featured) {
        String themeLabel = featured.theme().koreanName();
        return HomeBriefingDto.DetailTabs.builder()
                .summaryTab(HomeBriefingDto.SummaryTab.builder()
                        .judgement(mapJudgement(featured.direction(), featured.confidence()))
                        .exposurePercent(featured.exposurePercent())
                        .oneLineSummary(featured.oneLineReason())
                        .build())
                .evidenceTab(HomeBriefingDto.EvidenceTab.builder()
                        .impactPaths(List.of(
                                HomeBriefingDto.ImpactPathItem.builder().icon("policy").title("정책 변화")
                                        .description(themeLabel + " 변수 변화 신호 감지").build(),
                                HomeBriefingDto.ImpactPathItem.builder().icon("macro").title("거시 변수")
                                        .description("금리/달러/변동성 채널로 전이").build(),
                                HomeBriefingDto.ImpactPathItem.builder().icon("industry").title("산업/ETF")
                                        .description("민감도가 높은 ETF 및 자산군 우선 반응").build(),
                                HomeBriefingDto.ImpactPathItem.builder().icon("portfolio").title("내 자산")
                                        .description("보유 비중과 결합해 영향권 계산").build()))
                        .coreEvidences(List.of(
                                featured.oneLineReason(),
                                "유사 이벤트 대비 신뢰도 " + featured.confidence() + "%로 산출",
                                "노출 비중 " + featured.exposurePercent() + "% 구간 자산군에서 반응 민감도 우위"))
                        .counterEvidences(List.of(
                                "시장 컨센서스가 이미 가격에 반영되었을 가능성",
                                "동일 시점의 반대 거시 이슈(고용/환율)로 효과 상쇄 가능"))
                        .invalidationConditions(List.of(
                                "발표 직후 핵심 지표가 기준선에서 크게 이탈하지 않을 때",
                                "1시간 내 시장 반응 강도가 30% 이하로 유지될 때"))
                        .build())
                .build();
    }

    private HomeBriefingDto.CheckpointTab buildCheckpointTab(ScoredEvent featured) {
        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        return HomeBriefingDto.CheckpointTab.builder()
                .policyCheckpoints(List.of(
                        HomeBriefingDto.CheckpointItem.builder().title("정책 발표 핵심 수치").threshold("컨센서스 대비 ±0.25p")
                                .reason("기준선 이탈 시 방향성 재평가").build(),
                        HomeBriefingDto.CheckpointItem.builder().title("가이던스 톤").threshold("매파/비둘기 전환 문구")
                                .reason("행동 추천 신뢰도 보정").build()))
                .marketCheckpoints(List.of(
                        HomeBriefingDto.CheckpointItem.builder().title("장기채 ETF 반응").threshold("30분 변동률 1.0% 이상")
                                .reason("금리 민감 자산 반응 확인").build(),
                        HomeBriefingDto.CheckpointItem.builder().title("달러 인덱스 반응").threshold("동시 0.4% 이상 변동")
                                .reason("환율 채널 동조 여부 확인").build()))
                .revisitAlert("발표 +30분, 장중 +3시간, 장마감 전 재점검 알림 규칙 생성")
                .reflectionStatus(HomeBriefingDto.ReflectionStatus.builder()
                        .updatedAt(timestamp)
                        .sources(List.of("Federal Reserve", "정책/뉴스 크롤링 피드", "내부 ML 추론 결과"))
                        .reviewStatus("모델 실행 및 응답 생성 완료")
                        .build())
                .build();
    }

    private String resolveUserName(Long userId) {
        UserEntity user = userJpaRepository.findById(userId).orElse(null);
        if (user == null) {
            return "사용자";
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return "사용자";
    }

    private String resolveProfileInitial(String userName) {
        if (userName == null || userName.isBlank()) {
            return "U";
        }
        String normalized = userName.trim();
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private List<AssetPosition> loadAssetPositions(Long userId) {
        List<UserWatchAssetEntity> watchAssets = userWatchAssetRepository.findByUserIdOrderByDisplayOrderAsc(userId);
        List<WatchAssetDto.Asset> selected = watchAssets.isEmpty()
                ? watchAssetSelectionService.getSelectedAssets(userId)
                : watchAssets.stream()
                        .map(asset -> WatchAssetDto.Asset.builder()
                                .assetName(asset.getAssetName())
                                .changePercent(asset.getChangePercent())
                                .build())
                        .toList();

        if (selected.isEmpty()) {
            return List.of(new AssetPosition("현금성 자산", 1.0, 0.0));
        }

        double equalWeight = 1.0 / selected.size();
        return selected.stream()
                .map(asset -> new AssetPosition(asset.getAssetName(), equalWeight, asset.getChangePercent()))
                .collect(Collectors.toList());
    }

    private Theme classifyTheme(PolicyEventEntity event) {
        String token = ((event.getKeyword() == null ? "" : event.getKeyword()) + " "
                + (event.getTitle() == null ? "" : event.getTitle())).toLowerCase(Locale.ROOT);
        if (token.contains("semiconductor") || token.contains("반도체") || token.contains("chip")) {
            return Theme.SEMICONDUCTOR;
        }
        if (token.contains("dollar") || token.contains("달러") || token.contains("fx") || token.contains("환율")) {
            return Theme.DOLLAR;
        }
        if (token.contains("employment") || token.contains("고용")) {
            return Theme.EMPLOYMENT;
        }
        return Theme.RATE;
    }

    private int sensitivity(String assetName, Theme theme) {
        String normalized = assetName == null ? "" : assetName.toLowerCase(Locale.ROOT);
        return switch (theme) {
            case RATE -> {
                if (normalized.contains("장기채"))
                    yield 90;
                if (normalized.contains("나스닥") || normalized.contains("성장"))
                    yield 75;
                if (normalized.contains("코스피"))
                    yield 45;
                if (normalized.contains("달러"))
                    yield 35;
                yield 30;
            }
            case DOLLAR -> {
                if (normalized.contains("달러"))
                    yield 92;
                if (normalized.contains("금"))
                    yield 70;
                if (normalized.contains("나스닥"))
                    yield 40;
                yield 35;
            }
            case SEMICONDUCTOR -> {
                if (normalized.contains("나스닥") || normalized.contains("성장"))
                    yield 82;
                if (normalized.contains("코스피"))
                    yield 60;
                yield 30;
            }
            case EMPLOYMENT -> {
                if (normalized.contains("나스닥"))
                    yield 68;
                if (normalized.contains("장기채"))
                    yield 55;
                yield 30;
            }
        };
    }

    private String mapActionToAppTerm(String direction, int volatility, int confidence) {
        if (volatility >= 70) {
            return "방어 비중 점검";
        }
        if (confidence < 55) {
            return "기다리기";
        }
        if ("상승".equals(direction)) {
            return "분할 매수 검토";
        }
        return "리밸런싱 점검";
    }

    private String mapJudgement(String direction, int confidence) {
        if (confidence >= 75) {
            return "강한 " + direction + " 가능성";
        }
        if (confidence >= 60) {
            return direction + " 우위";
        }
        return "관망 필요";
    }

    private String mapShortJudgement(String direction, int confidence) {
        if (confidence >= 70) {
            return direction + " 우세";
        }
        if (confidence >= 55) {
            return direction + " 가능";
        }
        return "방향 혼조";
    }

    private String buildRiskSummary(String label, int aggregateRisk, int confidence) {
        return label + " 구간(" + aggregateRisk + "pt)이며, 현재 시그널 신뢰도는 " + confidence + "%입니다.";
    }

    private double safeImpact(PolicyEventEntity event) {
        if (event == null || event.getImpactScore() == null) {
            return 50.0;
        }
        return Math.max(0.0, Math.min(100.0, event.getImpactScore()));
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private enum Theme {
        RATE("금리"),
        DOLLAR("달러"),
        SEMICONDUCTOR("반도체"),
        EMPLOYMENT("고용");

        private final String koreanName;

        Theme(String koreanName) {
            this.koreanName = koreanName;
        }

        public String koreanName() {
            return koreanName;
        }
    }

    private record AssetPosition(String assetName, double weight, double changePercent) {
    }

    private record ScoredEvent(
            PolicyEventEntity event,
            String displayTitle,
            int exposurePercent,
            int volatility,
            int confidence,
            int upsideProbability,
            int downsideProbability,
            String direction,
            double priority,
            String shortJudgement,
            String oneLineReason,
            Theme theme) {
    }
}
