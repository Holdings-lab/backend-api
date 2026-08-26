package com.project.server.service.home;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.server.domain.HomeBriefingEntity;
import com.project.server.domain.PolicyEventEntity;
import com.project.server.domain.UserEntity;
import com.project.server.domain.UserWatchAssetEntity;
import com.project.server.dto.HomeDto;
import com.project.server.dto.HomeBriefingDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.PolicyEventJpaRepository;
import com.project.server.repository.HomeBriefingRepository;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.UserWatchAssetRepository;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.service.asset.AssetMetricsService;
import com.project.server.service.auth.WatchAssetSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class HomeService {

        private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        private final UserJpaRepository userJpaRepository;
        private final PolicyEventJpaRepository policyEventJpaRepository;
        private final HomeBriefingRepository homeBriefingRepository;
        private final UserWatchAssetRepository userWatchAssetRepository;
        private final WatchAssetSelectionService watchAssetSelectionService;
        private final AssetMetricsService assetMetricsService;
        private final AssetPositionRepository assetPositionRepository;
        private final BrokerAccountRepository brokerAccountRepository;
        private final FeaturedEventStateService featuredEventStateService;
        private final com.project.server.service.integration.MlPredictionProxyService mlPredictionProxyService;
        private final HomeBriefingLlmService homeBriefingLlmService;
        private final ObjectMapper objectMapper;

        public HomeDto.HomeResponse getHome(Long userId) {
                HomeContext context = buildHomeContext(userId);

                String name = context.displayName();
                PolicyEventEntity featuredEvent = context.featuredEvent();
                Map<String, Object> portfolioSummary = context.portfolioSummary();
                List<HomeDto.WatchAssetImpact> watchAssetImpacts = context.watchAssetImpacts();

                String featuredTitle = context.featuredTitle();
                String featuredSummary = context.featuredSummary();
                String dDayText = context.dDayText();

                FeaturedEventStateService.FeaturedEventState featured = featuredEventStateService.getFeatured(userId);
                if (featured != null) {
                        featuredTitle = featured.title();
                        featuredSummary = featured.summary();
                        dDayText = featured.dDayText();
                }

                HomeCopy copy = buildHomeCopy(name, featuredEvent, portfolioSummary, context.linkedAssetCount(),
                                featuredTitle, featuredSummary, dDayText);

                List<HomeDto.InsightCard> insights = buildInsights(featuredEvent, context.linkedAssetCount());
                List<HomeDto.ReasonItem> reasonItems = buildReasonItems(featuredSummary);

                return HomeDto.HomeResponse.builder()
                                .userGreeting(HomeDto.UserGreeting.builder()
                                                .displayName(name)
                                                .headline(copy.headline())
                                                .subtext(copy.subtext())
                                                .build())
                                .featuredEvent(HomeDto.FeaturedEvent.builder()
                                                .label(copy.featuredLabel())
                                                .dDayText(copy.dDayText())
                                                .title(copy.featuredTitle())
                                                .tags(featured == null ? buildTags(featuredEvent) : featured.tags())
                                                .summary(copy.featuredSummary())
                                                .metaText(copy.metaText())
                                                .build())
                                .learningCard(HomeDto.LearningCard.builder()
                                                .label(copy.learningLabel())
                                                .title(copy.learningTitle())
                                                .progressPercent(copy.learningProgress())
                                                .ctaText(copy.learningCta())
                                                .build())
                                .watchAssetImpacts(watchAssetImpacts)
                                .threeInsights(insights)
                                .reasonPanel(HomeDto.ReasonPanel.builder()
                                                .title(copy.reasonTitle())
                                                .sourceText(copy.reasonSourceText())
                                                .items(reasonItems)
                                                .ctaText(copy.reasonCta())
                                                .build())
                                .predictionReview(HomeDto.PredictionReview.builder()
                                                .title(copy.predictionReviewTitle())
                                                .eventName(copy.predictionReviewEventName())
                                                .summary(copy.predictionReviewSummary())
                                                .ctaText(copy.predictionReviewCta())
                                                .build())
                                .disclaimerText(copy.disclaimerText())
                                .build();
        }

        private String formatDDay(LocalDateTime targetTime) {
                Duration duration = Duration.between(LocalDateTime.now(), targetTime);
                if (duration.isNegative()) {
                        return "D-0 00:00";
                }
                long hours = duration.toHours();
                long minutes = duration.minusHours(hours).toMinutes();
                return String.format("D-0 %02d:%02d", hours, minutes);
        }

        private String resolveDisplayName(UserEntity user) {
                if (user.getNickname() != null && !user.getNickname().isBlank()) {
                        return user.getNickname();
                }
                if (user.getEmail() != null && !user.getEmail().isBlank()) {
                        return user.getEmail();
                }
                return "사용자";
        }

        public HomeBriefingDto.BriefingResponse getBriefing(Long userId) {
                BriefingBundle bundle = buildBriefingBundle(userId);
                return HomeBriefingDto.BriefingResponse.builder()
                                .homeHeader(bundle.homeHeader())
                                .featuredCard(bundle.featuredCard())
                                .portfolioCard(bundle.portfolioCard())
                                .secondarySignals(bundle.secondarySignals())
                                .quickInterpretation(bundle.quickInterpretation())
                                .detailTabs(bundle.detailTabs())
                                .checkpointTab(bundle.checkpointTab())
                                .briefingHeadline(bundle.briefingHeadline())
                                .briefingParagraphs(bundle.briefingParagraphs())
                                .pushTitle(bundle.pushTitle())
                                .pushBody(bundle.pushBody())
                                .briefingTone(bundle.briefingTone())
                                .disclaimer(bundle.disclaimer())
                                .build();
        }

        public HomeBriefingDto.HomeHeader getHomeHeader(Long userId) {
                return buildBriefingBundle(userId).homeHeader();
        }

        public HomeBriefingDto.FeaturedSignalCard getFeaturedCard(Long userId) {
                return buildBriefingBundle(userId).featuredCard();
        }

        public HomeBriefingDto.PortfolioCard getPortfolioCard(Long userId) {
                return buildBriefingBundle(userId).portfolioCard();
        }

        public List<HomeBriefingDto.SecondarySignalItem> getSecondarySignals(Long userId) {
                return buildBriefingBundle(userId).secondarySignals();
        }

        public HomeBriefingDto.QuickInterpretation getQuickInterpretation(Long userId) {
                return buildBriefingBundle(userId).quickInterpretation();
        }

        public HomeBriefingDto.DetailTabs getDetailTabs(Long userId) {
                return buildBriefingBundle(userId).detailTabs();
        }

        public HomeBriefingDto.CheckpointTab getCheckpointTab(Long userId) {
                return buildBriefingBundle(userId).checkpointTab();
        }

        public HomeBriefingDto.DisclaimerResponse getDisclaimer(Long userId) {
                return HomeBriefingDto.DisclaimerResponse.builder()
                                .disclaimer(buildBriefingBundle(userId).disclaimer())
                                .build();
        }

        private BriefingBundle buildBriefingBundle(Long userId) {
                Optional<BriefingBundle> cachedBundle = loadCachedBriefingBundle(userId);
                if (cachedBundle.isPresent()) {
                        return cachedBundle.get();
                }

                HomeContext context = buildHomeContext(userId);

                String userName = context.displayName();
                String profileInitial = resolveProfileInitial(userName);
                Map<String, Object> portfolioSummary = context.portfolioSummary();
                HomeCopy copy = buildHomeCopy(userName, context.featuredEvent(), portfolioSummary,
                                context.linkedAssetCount(), context.featuredTitle(), context.featuredSummary(),
                                context.dDayText());

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

                com.fasterxml.jackson.databind.JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();

                List<ScoredEvent> ranked = events.stream()
                                .map(event -> scoreEvent(event, positions, mlResult))
                                .sorted(Comparator.comparingDouble(ScoredEvent::priority).reversed())
                                .toList();

                ScoredEvent featured = ranked.get(0);
                List<ScoredEvent> secondary = ranked.stream().skip(1).limit(2).toList();

                int aggregateRisk = calculateAggregateRisk(ranked, positions);
                String riskLabel = aggregateRisk >= 75 ? "위험" : aggregateRisk >= 50 ? "주의" : "양호";

                Map<String, Integer> themeExposures = calculateThemeExposures(positions);
                String totalAssetText = formatCurrencyValue(portfolioSummary.get("totalAssets"));
                String returnRateText = formatRateValue(portfolioSummary.get("dailyReturnRate"));

                HomeBriefingDto.QuickInterpretation quickInterpretation = buildQuickInterpretation(featured);
                HomeBriefingDto.DetailTabs detailTabs = buildDetailTabs(featured);
                HomeBriefingDto.CheckpointTab checkpointTab = buildCheckpointTab(featured);

                Map<String, Object> briefingSnapshot = buildBriefingSnapshot(
                                userName,
                                context.featuredEvent(),
                                portfolioSummary,
                                context.linkedAssetCount(),
                                featured,
                                ranked,
                                aggregateRisk,
                                riskLabel,
                                totalAssetText,
                                returnRateText,
                                quickInterpretation,
                                detailTabs,
                                checkpointTab);

                HomeBriefingLlmService.Narrative narrative = homeBriefingLlmService.generateNarrative(briefingSnapshot);

                BriefingBundle bundle = new BriefingBundle(
                                HomeBriefingDto.HomeHeader.builder()
                                                .greeting(copy.headline())
                                                .userName(userName)
                                                .profileInitial(profileInitial)
                                                .build(),
                                HomeBriefingDto.FeaturedSignalCard.builder()
                                                .signalTitle(featured.displayTitle())
                                                .myAssetExposurePercent(featured.exposurePercent())
                                                .recommendedAction(
                                                                mapActionToAppTerm(featured.direction(),
                                                                                featured.volatility(),
                                                                                featured.confidence()))
                                                .judgement(mapJudgement(featured.direction(), featured.confidence()))
                                                .upsideProbability(featured.upsideProbability())
                                                .downsideProbability(featured.downsideProbability())
                                                .volatility(featured.volatility())
                                                .confidence(featured.confidence())
                                                .coreReason(featured.oneLineReason())
                                                .build(),
                                HomeBriefingDto.PortfolioCard.builder()
                                                .totalAsset(totalAssetText)
                                                .returnRate(returnRateText)
                                                .currentRiskLabel(riskLabel)
                                                .currentRiskSummary(buildRiskSummary(riskLabel, aggregateRisk,
                                                                featured.confidence()))
                                                .themeExposureBars(List.of(
                                                                HomeBriefingDto.ThemeExposureBar.builder().theme("금리")
                                                                                .exposurePercent(themeExposures
                                                                                                .getOrDefault("금리", 0))
                                                                                .build(),
                                                                HomeBriefingDto.ThemeExposureBar.builder().theme("반도체")
                                                                                .exposurePercent(themeExposures
                                                                                                .getOrDefault("반도체", 0))
                                                                                .build(),
                                                                HomeBriefingDto.ThemeExposureBar.builder().theme("달러")
                                                                                .exposurePercent(themeExposures
                                                                                                .getOrDefault("달러", 0))
                                                                                .build()))
                                                .build(),
                                secondary.stream().map(event -> HomeBriefingDto.SecondarySignalItem.builder()
                                                .title(event.displayTitle())
                                                .shortJudgement(event.shortJudgement())
                                                .exposurePercent(event.exposurePercent())
                                                .oneLineReason(event.oneLineReason())
                                                .build()).toList(),
                                quickInterpretation,
                                detailTabs,
                                checkpointTab,
                                narrative.headline(),
                                narrative.paragraphs(),
                                narrative.pushTitle(),
                                narrative.pushBody(),
                                narrative.providerName(),
                                narrative.modelName(),
                                narrative.tone(),
                                "본 정보는 투자 자문이 아니며, 학습 및 정보 제공 목적의 참고 자료입니다.");

                persistBriefingBundle(userId, bundle);
                return bundle;
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

        private ScoredEvent scoreEvent(PolicyEventEntity event, List<AssetPosition> positions,
                        com.fasterxml.jackson.databind.JsonNode mlResult) {
                double impact = safeImpact(event);
                Theme theme = classifyTheme(event);
                int exposure = clamp((int) Math.round(positions.stream()
                                .mapToDouble(position -> position.weight() * sensitivity(position.assetName(), theme))
                                .sum()));

                int volatility = clamp((int) Math.round(impact * 0.9 + (theme == Theme.DOLLAR ? 10 : 0)));
                int confidence = clamp((int) Math.round(55 + impact * 0.35));

                if (mlResult != null && !mlResult.isMissingNode()) {
                        double accuracy = mlResult.path("metrics").path("directionAccuracy").asDouble(0.0);
                        double mlScore = Math.abs(mlResult.path("metrics").path("policyScore").asDouble(0.0));
                        confidence = clamp((int) Math.round(accuracy * 100));
                        volatility = clamp((int) Math.round(mlScore * 2000));
                }

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
                                .mapToDouble(position -> Math.abs(position.changePercent()) * position.weight()).sum()
                                * 14;
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
                                        .mapToDouble(position -> position.weight()
                                                        * sensitivity(position.assetName(), mapped))
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
                                .myAssetImpact("내 자산 영향권 " + featured.exposurePercent() + "% · "
                                                + featured.shortJudgement())
                                .coreReason(featured.oneLineReason())
                                .keyNumbers(List.of(
                                                HomeBriefingDto.KeyNumberItem.builder().label("상승 가능성")
                                                                .baseline(featured.upsideProbability() + "%")
                                                                .description("이벤트 및 민감도 합산 추정치").build(),
                                                HomeBriefingDto.KeyNumberItem.builder().label("하락 가능성")
                                                                .baseline(featured.downsideProbability() + "%")
                                                                .description("반대 시나리오 확률").build(),
                                                HomeBriefingDto.KeyNumberItem.builder().label("변동성")
                                                                .baseline(featured.volatility() + "pt")
                                                                .description("정책 영향 강도 기반 위험 추정").build()))
                                .revisitTime("발표 후 30분 내 재확인, 이후 장 마감 전 한 번 더 점검 권장")
                                .weakenCondition("핵심 수치가 컨센서스와 반대로 나오거나 시장 반응 강도가 절반 미만이면 판단이 약화됩니다.")
                                .tip(mapActionToAppTerm(featured.direction(), featured.volatility(),
                                                featured.confidence()))
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
                                                                HomeBriefingDto.ImpactPathItem.builder().icon("policy")
                                                                                .title("정책 변화")
                                                                                .description(themeLabel
                                                                                                + " 변수 변화 신호 감지")
                                                                                .build(),
                                                                HomeBriefingDto.ImpactPathItem.builder().icon("macro")
                                                                                .title("거시 변수")
                                                                                .description("금리/달러/변동성 채널로 전이")
                                                                                .build(),
                                                                HomeBriefingDto.ImpactPathItem.builder()
                                                                                .icon("industry").title("산업/ETF")
                                                                                .description("민감도가 높은 ETF 및 자산군 우선 반응")
                                                                                .build(),
                                                                HomeBriefingDto.ImpactPathItem.builder()
                                                                                .icon("portfolio").title("내 자산")
                                                                                .description("보유 비중과 결합해 영향권 계산")
                                                                                .build()))
                                                .coreEvidences(List.of(
                                                                featured.oneLineReason(),
                                                                "유사 이벤트 대비 신뢰도 " + featured.confidence() + "%로 산출",
                                                                "노출 비중 " + featured.exposurePercent()
                                                                                + "% 구간 자산군에서 반응 민감도 우위"))
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
                                                HomeBriefingDto.CheckpointItem.builder().title("정책 발표 핵심 수치")
                                                                .threshold("컨센서스 대비 ±0.25p")
                                                                .reason("기준선 이탈 시 방향성 재평가").build(),
                                                HomeBriefingDto.CheckpointItem.builder().title("가이던스 톤")
                                                                .threshold("매파/비둘기 전환 문구")
                                                                .reason("행동 추천 신뢰도 보정").build()))
                                .marketCheckpoints(List.of(
                                                HomeBriefingDto.CheckpointItem.builder().title("장기채 ETF 반응")
                                                                .threshold("30분 변동률 1.0% 이상")
                                                                .reason("금리 민감 자산 반응 확인").build(),
                                                HomeBriefingDto.CheckpointItem.builder().title("달러 인덱스 반응")
                                                                .threshold("동시 0.4% 이상 변동")
                                                                .reason("환율 채널 동조 여부 확인").build()))
                                .revisitAlert("발표 +30분, 장중 +3시간, 장마감 전 재점검 알림 규칙 생성")
                                .reflectionStatus(HomeBriefingDto.ReflectionStatus.builder()
                                                .updatedAt(timestamp)
                                                .sources(List.of("Federal Reserve", "정책/뉴스 크롤링 피드", "내부 ML 추론 결과"))
                                                .reviewStatus("모델 실행 및 응답 생성 완료")
                                                .build())
                                .build();
        }

        private String resolveProfileInitial(String userName) {
                if (userName == null || userName.isBlank()) {
                        return "U";
                }
                String normalized = userName.trim();
                return normalized.substring(0, 1).toUpperCase(Locale.ROOT);
        }

        private List<AssetPosition> loadAssetPositions(Long userId) {
                if (assetMetricsService.hasConnectedAccounts(userId)) {
                        BigDecimal totalAsset = assetMetricsService.getAssetTotal(userId);
                        List<AssetPositionEntity> positions = brokerAccountRepository.findByUserId(userId).stream()
                                        .filter(account -> account.getConnectionStatus() == BrokerAccountEntity.ConnectionStatus.CONNECTED)
                                        .flatMap(account -> assetPositionRepository.findByAccountId(account.getId()).stream())
                                        .toList();

                        if (!positions.isEmpty()) {
                                return positions.stream()
                                                .map(position -> {
                                                        BigDecimal currentValue = position.getCurrentValue() != null
                                                                        ? position.getCurrentValue()
                                                                        : BigDecimal.ZERO;
                                                        double weight = totalAsset.compareTo(BigDecimal.ZERO) > 0
                                                                        ? currentValue.divide(totalAsset, 6, RoundingMode.HALF_UP)
                                                                                        .doubleValue()
                                                                        : 0.0;
                                                        double changePercent = position.getGainLossRate() != null
                                                                        ? position.getGainLossRate().doubleValue()
                                                                        : 0.0;
                                                        String assetName = position.getItemName() != null
                                                                        ? position.getItemName()
                                                                        : (position.getSymbol() != null
                                                                                        ? position.getSymbol()
                                                                                        : "보유종목");
                                                        return new AssetPosition(assetName, weight, changePercent);
                                                })
                                                .collect(Collectors.toList());
                        }
                }

                List<UserWatchAssetEntity> watchAssets = userWatchAssetRepository
                                .findByUserIdOrderByDisplayOrderAsc(userId);
                List<com.project.server.dto.WatchAssetDto.Asset> selected = watchAssets.isEmpty()
                                ? watchAssetSelectionService.getSelectedAssets(userId)
                                : watchAssets.stream()
                                                .map(asset -> com.project.server.dto.WatchAssetDto.Asset.builder()
                                                                .assetName(asset.getAssetName())
                                                                .changePercent(asset.getChangePercent())
                                                                .build())
                                                .toList();

                if (selected.isEmpty()) {
                        return List.of(new AssetPosition("현금성 자산", 1.0, 0.0));
                }

                double equalWeight = 1.0 / selected.size();
                return selected.stream()
                                .map(asset -> new AssetPosition(asset.getAssetName(), equalWeight,
                                                asset.getChangePercent()))
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

        private void ensureUserExists(Long userId) {
                boolean exists = userJpaRepository.existsById(userId);
                if (!exists) {
                        throw ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND");
                }
        }

        private HomeContext buildHomeContext(Long userId) {
                ensureUserExists(userId);

                UserEntity user = userJpaRepository.findById(userId).orElseThrow();
                PolicyEventEntity featuredEvent = policyEventJpaRepository.findTopByOrderByCreatedAtDesc()
                                .orElse(null);
                Map<String, Object> portfolioSummary = loadPortfolioSummary(userId);
                List<UserWatchAssetEntity> watchAssets = userWatchAssetRepository
                                .findByUserIdOrderByDisplayOrderAsc(userId);
                List<HomeDto.WatchAssetImpact> watchAssetImpacts = watchAssets.isEmpty()
                                ? watchAssetSelectionService.getWatchImpacts(userId)
                                : watchAssets.stream()
                                                .map(asset -> HomeDto.WatchAssetImpact.builder()
                                                                .assetName(asset.getAssetName())
                                                                .signalText(asset.getSignalText())
                                                                .build())
                                                .toList();

                int linkedAssetCount = resolveLinkedAssetCount(userId, watchAssetImpacts.size());

                return new HomeContext(
                                userId,
                                user,
                                resolveDisplayName(user),
                                featuredEvent,
                                portfolioSummary,
                                watchAssetImpacts,
                                linkedAssetCount,
                                resolveFeaturedTitle(featuredEvent),
                                resolveFeaturedSummary(featuredEvent),
                                resolveDDayText(featuredEvent));
        }

        private int resolveLinkedAssetCount(Long userId, int watchAssetCount) {
                if (!assetMetricsService.hasConnectedAccounts(userId)) {
                        return watchAssetCount;
                }
                long positionCount = brokerAccountRepository.findByUserId(userId).stream()
                                .filter(account -> account.getConnectionStatus() == BrokerAccountEntity.ConnectionStatus.CONNECTED)
                                .flatMap(account -> assetPositionRepository.findByAccountId(account.getId()).stream())
                                .map(position -> position.getSymbol() != null ? position.getSymbol() : position.getItemCode())
                                .filter(symbol -> symbol != null && !symbol.isBlank())
                                .distinct()
                                .count();
                return positionCount > 0 ? (int) positionCount : watchAssetCount;
        }

        private HomeCopy buildHomeCopy(String displayName, PolicyEventEntity featuredEvent,
                        Map<String, Object> portfolioSummary, int linkedAssetCount, String featuredTitle,
                        String featuredSummary, String dDayText) {
                String totalAssetText = formatCurrencyValue(portfolioSummary.get("totalAssets"));
                String returnRateText = formatRateValue(portfolioSummary.get("dailyReturnRate"));
                String eventText = featuredEvent == null ? "최신 이벤트" : resolveFeaturedTitle(featuredEvent);
                int progress = (int) Math.round((featuredEvent == null || featuredEvent.getImpactScore() == null ? 50.0
                                : featuredEvent.getImpactScore()) * 0.5 + linkedAssetCount * 10.0);

                return new HomeCopy(
                                "안녕하세요, " +displayName + "님",
                                totalAssetText + " 보유 자산과 " + linkedAssetCount + "개 연동 자산 기준으로 " + eventText
                                                + " 반응과 수익률 " + returnRateText + "를 확인합니다.",
                                featuredEvent == null ? "이벤트 대기"
                                                : (featuredEvent.getKeyword() != null && !featuredEvent.getKeyword().isBlank()
                                                                ? featuredEvent.getKeyword() + " 핵심 이벤트"
                                                                : "핵심 이벤트"),
                                featuredTitle,
                                featuredSummary,
                                dDayText,
                                totalAssetText + " 기준 · " + linkedAssetCount + "개 자산 연결 · 일간 수익률 "
                                                + returnRateText,
                                featuredEvent == null || featuredEvent.getKeyword() == null
                                                || featuredEvent.getKeyword().isBlank()
                                                        ? "연결 학습"
                                                        : featuredEvent.getKeyword() + " 학습",
                                featuredEvent != null && featuredEvent.getTitle() != null
                                                && !featuredEvent.getTitle().isBlank()
                                                        ? featuredEvent.getTitle()
                                                        : (featuredEvent != null && featuredEvent.getKeyword() != null
                                                                        && !featuredEvent.getKeyword().isBlank()
                                                                                ? featuredEvent.getKeyword()
                                                                                                + " 관련 포인트 정리"
                                                                                : "오늘의 시장 포인트 정리"),
                                Math.max(0, Math.min(100, progress)),
                                featuredEvent == null ? "학습 준비하기" : "관련 설명 보기",
                                featuredEvent != null && featuredEvent.getKeyword() != null
                                                && !featuredEvent.getKeyword().isBlank()
                                                        ? featuredEvent.getKeyword() + " 판단 근거"
                                                        : "판단 근거",
                                eventText + " · " + linkedAssetCount + "개 연동 자산 · " + totalAssetText + " 기준",
                                featuredEvent == null ? "근거 확인" : "이유 자세히 보기",
                                featuredEvent != null && featuredEvent.getKeyword() != null
                                                && !featuredEvent.getKeyword().isBlank()
                                                        ? featuredEvent.getKeyword() + " 예측 복기"
                                                        : "예측 복기",
                                eventText,
                                featuredEvent == null
                                        ? "현재 포트폴리오 일간 수익률 " + returnRateText + ", 수익금 "
                                                        + formatCurrencyValue(portfolioSummary.get("dailyReturnAmount"))
                                                        + " 기준으로 복기 자료를 준비 중입니다."
                                        : eventText + " 발표 이후 포트폴리오 수익률 " + returnRateText + ", 일간 수익금 "
                                                        + formatCurrencyValue(portfolioSummary.get("dailyReturnAmount"))
                                                        + "를 기록했습니다.",
                                featuredEvent == null ? "기록 보기" : "복기하기",
                                "본 정보는 " + (featuredEvent == null ? "학습용 시뮬레이션" : eventText)
                                                + " 기반 참고 자료이며, 실제 투자 판단의 근거로 사용하지 마세요.");
        }

        private List<String> buildTags(PolicyEventEntity featuredEvent) {
                if (featuredEvent == null) {
                        return List.of("대기", "정책", "기본");
                }
                String keyword = featuredEvent.getKeyword() == null ? "정책" : featuredEvent.getKeyword();
                String impactTag = featuredEvent.getImpactScore() == null
                                ? "보통"
                                : (featuredEvent.getImpactScore() >= 70 ? "매우높음"
                                                : featuredEvent.getImpactScore() >= 50 ? "높음" : "보통");
                return List.of("정책", keyword, impactTag);
        }

        private Map<String, Object> loadPortfolioSummary(Long userId) {
                AssetMetricsService.AssetMetrics metrics = assetMetricsService.compute(userId);
                BigDecimal totalAssets = metrics.assetTotal();
                BigDecimal dailyReturnRate = metrics.dailyChangePct();
                BigDecimal dailyReturnAmount = totalAssets.multiply(dailyReturnRate)
                                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

                Map<String, Object> summary = new HashMap<>();
                summary.put("totalAssets", totalAssets);
                summary.put("dailyReturnRate", dailyReturnRate);
                summary.put("dailyReturnAmount", dailyReturnAmount);
                return summary;
        }

        private String formatCurrencyValue(Object value) {
                if (value instanceof Number number) {
                        return String.format(Locale.US, "%,.0f원", number.doubleValue());
                }
                return "0원";
        }

        private String formatRateValue(Object value) {
                if (value instanceof Number number) {
                        return String.format(Locale.US, "%+.2f%%", number.doubleValue());
                }
                return "0.00%";
        }

        private String resolveFeaturedTitle(PolicyEventEntity featuredEvent) {
                if (featuredEvent == null) {
                        return "최신 정책 이벤트 대기";
                }
                if (featuredEvent.getTitle() != null && !featuredEvent.getTitle().isBlank()) {
                        return featuredEvent.getTitle();
                }
                if (featuredEvent.getKeyword() != null && !featuredEvent.getKeyword().isBlank()) {
                        return featuredEvent.getKeyword() + " 관련 정책 이벤트";
                }
                return "정책 이벤트";
        }

        private String resolveFeaturedSummary(PolicyEventEntity featuredEvent) {
                if (featuredEvent == null) {
                        return "등록된 정책 이벤트가 없어 최신 요약을 준비 중입니다.";
                }
                if (featuredEvent.getAnalysisSummary() != null && !featuredEvent.getAnalysisSummary().isBlank()) {
                        return featuredEvent.getAnalysisSummary();
                }
                return resolveFeaturedTitle(featuredEvent) + "에 대한 세부 요약이 아직 없습니다.";
        }

        private String resolveDDayText(PolicyEventEntity featuredEvent) {
                if (featuredEvent == null || featuredEvent.getCreatedAt() == null) {
                        return "일정 없음";
                }
                return formatDDay(featuredEvent.getCreatedAt().plusHours(24));
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

        private record HomeCopy(
                        String headline,
                        String subtext,
                        String featuredLabel,
                        String featuredTitle,
                        String featuredSummary,
                        String dDayText,
                        String metaText,
                        String learningLabel,
                        String learningTitle,
                        int learningProgress,
                        String learningCta,
                        String reasonTitle,
                        String reasonSourceText,
                        String reasonCta,
                        String predictionReviewTitle,
                        String predictionReviewEventName,
                        String predictionReviewSummary,
                        String predictionReviewCta,
                        String disclaimerText) {
        }

        private record HomeContext(
                        Long userId,
                        UserEntity user,
                        String displayName,
                        PolicyEventEntity featuredEvent,
                        Map<String, Object> portfolioSummary,
                        List<HomeDto.WatchAssetImpact> watchAssetImpacts,
                        int linkedAssetCount,
                        String featuredTitle,
                        String featuredSummary,
                        String dDayText) {
        }

        private record BriefingBundle(
                        HomeBriefingDto.HomeHeader homeHeader,
                        HomeBriefingDto.FeaturedSignalCard featuredCard,
                        HomeBriefingDto.PortfolioCard portfolioCard,
                        List<HomeBriefingDto.SecondarySignalItem> secondarySignals,
                        HomeBriefingDto.QuickInterpretation quickInterpretation,
                        HomeBriefingDto.DetailTabs detailTabs,
                        HomeBriefingDto.CheckpointTab checkpointTab,
                        String briefingHeadline,
                        List<String> briefingParagraphs,
                        String pushTitle,
                        String pushBody,
                        String llmProvider,
                        String llmModel,
                        String briefingTone,
                        String disclaimer) {
        }

        private Optional<BriefingBundle> loadCachedBriefingBundle(Long userId) {
                try {
                        LocalDate briefingDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
                        Optional<HomeBriefingEntity> cached = homeBriefingRepository
                                        .findTopByUserIdAndBriefingDateOrderByUpdatedAtDesc(userId, briefingDate);
                        if (cached.isEmpty()) {
                                return Optional.empty();
                        }

                        return Optional.ofNullable(toBriefingBundle(cached.get()));
                } catch (Exception exception) {
                        return Optional.empty();
                }
        }

        private void persistBriefingBundle(Long userId, BriefingBundle bundle) {
                try {
                        HomeBriefingDto.BriefingResponse response = toBriefingResponse(bundle);
                        LocalDate briefingDate = LocalDate.now(ZoneId.of("Asia/Seoul"));
                        Map<String, Object> pushData = new LinkedHashMap<>();
                        pushData.put("title", bundle.pushTitle());
                        pushData.put("body", bundle.pushBody());
                        pushData.put("tone", bundle.briefingTone());

                        HomeBriefingEntity entity = HomeBriefingEntity.builder()
                                        .userId(userId)
                                        .briefingDate(briefingDate)
                                        .briefingHeadline(bundle.briefingHeadline())
                                        .briefingParagraphsJson(objectMapper.writeValueAsString(bundle.briefingParagraphs()))
                                        .pushDataJson(objectMapper.writeValueAsString(pushData))
                                        .llmProvider(bundle.llmProvider())
                                        .llmModel(bundle.llmModel())
                                        .promptVersion("home-briefing-v1")
                                        .briefingPayloadJson(objectMapper.writeValueAsString(response))
                                        .build();
                        homeBriefingRepository.save(entity);
                } catch (Exception exception) {
                        // cache persistence is best-effort; response must still succeed
                }
        }

        private BriefingBundle toBriefingBundle(HomeBriefingEntity entity) {
                try {
                        HomeBriefingDto.BriefingResponse response = objectMapper.readValue(
                                        entity.getBriefingPayloadJson(),
                                        HomeBriefingDto.BriefingResponse.class);
                        return new BriefingBundle(
                                        response.getHomeHeader(),
                                        response.getFeaturedCard(),
                                        response.getPortfolioCard(),
                                        response.getSecondarySignals() == null ? List.of() : response.getSecondarySignals(),
                                        response.getQuickInterpretation(),
                                        response.getDetailTabs(),
                                        response.getCheckpointTab(),
                                        response.getBriefingHeadline(),
                                        response.getBriefingParagraphs() == null ? List.of() : response.getBriefingParagraphs(),
                                        response.getPushTitle(),
                                        response.getPushBody(),
                                        entity.getLlmProvider(),
                                        entity.getLlmModel(),
                                        response.getBriefingTone(),
                                        response.getDisclaimer());
                } catch (Exception exception) {
                        return null;
                }
        }

        private HomeBriefingDto.BriefingResponse toBriefingResponse(BriefingBundle bundle) {
                return HomeBriefingDto.BriefingResponse.builder()
                                .homeHeader(bundle.homeHeader())
                                .featuredCard(bundle.featuredCard())
                                .portfolioCard(bundle.portfolioCard())
                                .secondarySignals(bundle.secondarySignals())
                                .quickInterpretation(bundle.quickInterpretation())
                                .detailTabs(bundle.detailTabs())
                                .checkpointTab(bundle.checkpointTab())
                                .briefingHeadline(bundle.briefingHeadline())
                                .briefingParagraphs(bundle.briefingParagraphs())
                                .pushTitle(bundle.pushTitle())
                                .pushBody(bundle.pushBody())
                                .briefingTone(bundle.briefingTone())
                                .disclaimer(bundle.disclaimer())
                                .build();
        }

        private Map<String, Object> buildBriefingSnapshot(
                        String userName,
                        PolicyEventEntity featuredEvent,
                        Map<String, Object> portfolioSummary,
                        int watchAssetCount,
                        ScoredEvent featured,
                        List<ScoredEvent> ranked,
                        int aggregateRisk,
                        String riskLabel,
                        String totalAssetText,
                        String returnRateText,
                        HomeBriefingDto.QuickInterpretation quickInterpretation,
                        HomeBriefingDto.DetailTabs detailTabs,
                        HomeBriefingDto.CheckpointTab checkpointTab) {
                Map<String, Object> snapshot = new LinkedHashMap<>();
                snapshot.put("userName", userName);
                snapshot.put("featuredTitle", featured.displayTitle());
                snapshot.put("featuredSummary", featured.oneLineReason());
                snapshot.put("featureDirection", featured.direction());
                snapshot.put("confidence", featured.confidence());
                snapshot.put("volatility", featured.volatility());
                snapshot.put("upsideProbability", featured.upsideProbability());
                snapshot.put("downsideProbability", featured.downsideProbability());
                snapshot.put("riskLabel", riskLabel);
                snapshot.put("riskSummary", buildRiskSummary(riskLabel, aggregateRisk, featured.confidence()));
                snapshot.put("watchAssetCount", watchAssetCount);
                snapshot.put("portfolioTotalAsset", totalAssetText);
                snapshot.put("portfolioReturnRate", returnRateText);
                snapshot.put("secondarySignalCount", ranked == null ? 0 : Math.max(0, ranked.size() - 1));
                snapshot.put("featuredEventTitle", featuredEvent == null ? null : featuredEvent.getTitle());
                snapshot.put("featuredEventKeyword", featuredEvent == null ? null : featuredEvent.getKeyword());
                snapshot.put("quickInterpretation", quickInterpretation);
                snapshot.put("detailTabs", detailTabs);
                snapshot.put("checkpointTab", checkpointTab);
                snapshot.put("generatedAt", LocalDateTime.now().format(DATE_TIME_FORMATTER));
                return snapshot;
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

        

        private List<HomeDto.InsightCard> buildInsights(PolicyEventEntity featuredEvent, int linkedAssetCount) {
                com.fasterxml.jackson.databind.JsonNode mlResult = mlPredictionProxyService.fetchPredictionResult();

                double impact = featuredEvent == null || featuredEvent.getImpactScore() == null ? 50.0
                                : featuredEvent.getImpactScore();
                int direction = (int) Math.round(Math.max(5, Math.min(95, impact)));
                int volatility = (int) Math.round(Math.max(10, Math.min(95, impact * 0.9 + linkedAssetCount * 4.0)));
                String confidence = impact >= 75 ? "높음" : impact >= 55 ? "중간" : "낮음";

                String detail = featuredEvent == null || featuredEvent.getKeyword() == null
                                || featuredEvent.getKeyword().isBlank()
                                                ? "정책 이벤트 반영"
                                                : featuredEvent.getKeyword().toUpperCase(Locale.ROOT) + " 영향 반영";

                if (mlResult != null && !mlResult.isMissingNode()) {
                        double accuracy = mlResult.path("metrics").path("directionAccuracy").asDouble(0.0);
                        double mlScore = Math.abs(mlResult.path("metrics").path("policyScore").asDouble(0.0));
                        double topProb = mlResult.path("metrics").path("topLabelProbability").asDouble(0.0);

                        confidence = accuracy >= 0.75 ? "높음" : accuracy >= 0.55 ? "중간" : "낮음";
                        volatility = (int) Math.round(Math.max(10, Math.min(95, mlScore * 2000)));
                        direction = (int) Math.round(Math.max(5, Math.min(95, topProb * 100)));
                        detail = "data-ml 최신 분석 반영";
                }

                return List.of(
                                HomeDto.InsightCard.builder().title(direction + "%").subtitle("상승확률").detail(detail)
                                                .build(),
                                HomeDto.InsightCard.builder().title(volatility + "%").subtitle("변동성").detail("ML 예측 반영")
                                                .build(),
                                HomeDto.InsightCard.builder().title(confidence).subtitle("신뢰도").detail("DB 최신 이벤트 기반")
                                                .build());
        }

        private List<HomeDto.ReasonItem> buildReasonItems(String summary) {
                if (summary == null || summary.isBlank()) {
                        return List.of(
                                        HomeDto.ReasonItem.builder().rank(1).label("정책 원문 요약 반영").score(40).build(),
                                        HomeDto.ReasonItem.builder().rank(2).label("관심 자산 민감도 반영").score(30).build(),
                                        HomeDto.ReasonItem.builder().rank(3).label("최신 이벤트 우선순위 반영").score(20).build());
                }

                String compact = summary.replace("\n", " ").trim();
                String item1 = compact.length() > 18 ? compact.substring(0, 18) : compact;
                String item2 = compact.length() > 38 ? compact.substring(18, 38) : "시장 변동성 채널 반영";
                String item3 = compact.length() > 58 ? compact.substring(38, 58) : "관심 자산 노출도 반영";

                return List.of(
                                HomeDto.ReasonItem.builder().rank(1).label(item1).score(40).build(),
                                HomeDto.ReasonItem.builder().rank(2).label(item2).score(28).build(),
                                HomeDto.ReasonItem.builder().rank(3).label(item3).score(18).build());
        }
}
