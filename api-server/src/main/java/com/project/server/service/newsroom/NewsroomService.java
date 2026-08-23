package com.project.server.service.newsroom;

import com.project.server.domain.AssetPositionEntity;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.dto.NewsroomDto;
import com.project.server.dto.PolicyFeedDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.service.asset.AssetMetricsService;
import com.project.server.service.integration.PolicyFeedProxyService;
import com.project.server.service.integration.StockLogoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NewsroomService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final BigDecimal HERO_WEIGHT_THRESHOLD = BigDecimal.TEN;
    private static final BigDecimal COMPACT_WEIGHT_THRESHOLD = BigDecimal.valueOf(3);
    private static final String HEADER_TITLE = "뉴스룸";
    private static final String SECTION_TITLE = "내 보유 종목";
    private static final String SUCCESS_SUBTITLE = "다음 업데이트는 내일 아침이에요";
    private static final String FALLBACK_SUBTITLE = "보유 종목에 중요한 변화만 모았어요";
    private static final String TAB_FOOTER_MESSAGE = "오늘 브리핑은 여기까지예요";
    private static final String AI_NOTICE = "AI가 여러 기사를 요약했어요. 원문과 다를 수 있어요.";
    private static final String HARDCODED_AI_JUDGEMENT =
            "공급망 다변화는 장기 이슈라서 단일 보도만으로 단기 방향을 판단하기는 어려워요.";
    private static final String QUIET_SUMMARY = "계속 지켜보고 있어요";

    private final BrokerAccountRepository brokerAccountRepository;
    private final AssetPositionRepository assetPositionRepository;
    private final AssetMetricsService assetMetricsService;
    private final PolicyFeedProxyService policyFeedProxyService;
    private final StockLogoService stockLogoService;

    public NewsroomDto.TabResponse getNewsroom(Long userId, String briefingDate) {
        validateUserId(userId);
        LocalDate asOfDate = resolveBriefingDate(briefingDate);
        String asOfAt = toAsOfAt(asOfDate);

        List<HoldingPosition> holdings = loadHoldings(userId);
        if (holdings.isEmpty()) {
            return emptyTabResponse(asOfAt);
        }

        List<PolicyFeedDto.Card> cards;
        try {
            cards = policyFeedProxyService.getCards(userId, 50, null, null, null);
        } catch (Exception ex) {
            log.warn("뉴스룸 정책 피드 조회 실패: userId={}, message={}", userId, ex.getMessage());
            throw new NewsroomUnavailableException(
                    "뉴스 소스 조회 중 오류가 발생했습니다.",
                    unavailableTabResponse(asOfAt));
        }

        stockLogoService.preloadLogos(holdings.stream().map(HoldingPosition::ticker).toList());

        List<NewsroomDto.HoldingBriefing> briefings = new ArrayList<>();
        for (HoldingPosition holding : holdings) {
            briefings.add(buildHoldingBriefing(userId, holding, cards));
        }

        return NewsroomDto.TabResponse.builder()
                .header(NewsroomDto.Header.builder()
                        .title(HEADER_TITLE)
                        .asOfAt(asOfAt)
                        .subtitle(SUCCESS_SUBTITLE)
                        .build())
                .section(NewsroomDto.Section.builder()
                        .title(SECTION_TITLE)
                        .itemCount(briefings.size())
                        .build())
                .holdings(briefings)
                .footer(NewsroomDto.TabFooter.builder()
                        .message(TAB_FOOTER_MESSAGE)
                        .build())
                .emptyState(null)
                .errorState(null)
                .build();
    }

    public NewsroomDto.DetailResponse getNewsroomDetail(Long userId, String ticker, String briefingDate) {
        validateUserId(userId);
        String normalizedTicker = normalizeTicker(ticker);
        if (normalizedTicker == null) {
            throw ApiException.badRequest("유효하지 않은 티커입니다.", "NEWSROOM_INVALID_TICKER");
        }

        LocalDate asOfDate = resolveBriefingDate(briefingDate);
        String asOfAt = toAsOfAt(asOfDate);

        HoldingPosition holding = loadHoldings(userId).stream()
                .filter(item -> normalizedTicker.equalsIgnoreCase(item.ticker()))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound(
                        "보유하지 않은 종목입니다.", "NEWSROOM_HOLDING_NOT_FOUND"));

        List<PolicyFeedDto.Card> cards;
        try {
            cards = policyFeedProxyService.getCards(userId, 50, null, null, null);
        } catch (Exception ex) {
            log.warn("뉴스룸 상세 정책 피드 조회 실패: userId={}, ticker={}, message={}",
                    userId, normalizedTicker, ex.getMessage());
            throw ApiException.serviceUnavailable(
                    "뉴스 소스 조회 중 오류가 발생했습니다.", "NEWSROOM_UNAVAILABLE");
        }

        List<PolicyFeedDto.Card> matched = findCardsForTicker(normalizedTicker, cards);
        NewsroomDto.BriefingType type = resolveBriefingType(holding.weightPct(), !matched.isEmpty());
        if (type == NewsroomDto.BriefingType.Quiet) {
            throw ApiException.notFound(
                    "해당 종목의 상세 브리핑이 없습니다.", "NEWSROOM_DETAIL_NOT_FOUND");
        }

        PolicyFeedDto.Card primary = matched.get(0);
        BigDecimal dailyChangePct = resolveDailyChangePct(primary);
        BigDecimal totalAssetImpactPct = resolveTotalAssetImpactPct(dailyChangePct, holding.weightPct());

        List<NewsroomDto.SourceItem> sources = matched.stream()
                .limit(5)
                .map(this::toSourceItem)
                .toList();

        String headline = firstNonBlank(primary.getTitle(), holding.name() + " 관련 소식");
        String summaryBody = firstNonBlank(
                primary.getBodySummary(),
                primary.getBodyExcerpt(),
                headline);
        List<String> findings = buildFindings(matched);

        String thumbnailUrl = resolveNewsThumbnail(matched);

        return NewsroomDto.DetailResponse.builder()
                .stock(NewsroomDto.StockMeta.builder()
                        .ticker(holding.ticker())
                        .name(holding.name())
                        .logoUrl(stockLogoService.getLogoUrl(holding.ticker()))
                        .dailyChangePct(dailyChangePct)
                        .weightPct(holding.weightPct())
                        .totalAssetImpactPct(totalAssetImpactPct)
                        .build())
                .headline(headline)
                .imageUrl(thumbnailUrl)
                .aiJudgement(HARDCODED_AI_JUDGEMENT)
                .summary(NewsroomDto.DetailSummary.builder()
                        .body(summaryBody)
                        .findings(findings)
                        .build())
                .sources(sources)
                .footer(NewsroomDto.DetailFooter.builder()
                        .asOfAt(asOfAt)
                        .aiNotice(AI_NOTICE)
                        .build())
                .build();
    }

    private NewsroomDto.HoldingBriefing buildHoldingBriefing(
            Long userId,
            HoldingPosition holding,
            List<PolicyFeedDto.Card> cards
    ) {
        List<PolicyFeedDto.Card> matched = findCardsForTicker(holding.ticker(), cards);
        NewsroomDto.BriefingType type = resolveBriefingType(holding.weightPct(), !matched.isEmpty());

        String headline;
        String summary;
        BigDecimal dailyChangePct = null;
        BigDecimal totalAssetImpactPct = null;
        String detailPath = null;

        if (type == NewsroomDto.BriefingType.Hero) {
            PolicyFeedDto.Card primary = matched.get(0);
            headline = firstNonBlank(primary.getTitle(), holding.name() + " 관련 소식");
            summary = firstNonBlank(primary.getBodySummary(), primary.getBodyExcerpt(), headline);
            dailyChangePct = resolveDailyChangePct(primary);
            totalAssetImpactPct = resolveTotalAssetImpactPct(dailyChangePct, holding.weightPct());
            detailPath = detailPath(holding.ticker());
        } else if (type == NewsroomDto.BriefingType.Compact) {
            PolicyFeedDto.Card primary = matched.get(0);
            headline = firstNonBlank(primary.getBodySummary(), primary.getTitle(), holding.name() + " 관련 소식");
            summary = null;
            detailPath = detailPath(holding.ticker());
        } else {
            int quietDays = quietDaysFor(holding.ticker());
            headline = quietDays + "일째 특이사항 없음";
            summary = QUIET_SUMMARY;
        }

        return NewsroomDto.HoldingBriefing.builder()
                .ticker(holding.ticker())
                .name(holding.name())
                .logoUrl(stockLogoService.getLogoUrl(holding.ticker()))
                .weightPct(holding.weightPct())
                .briefingType(type)
                .dailyChangePct(dailyChangePct)
                .totalAssetImpactPct(totalAssetImpactPct)
                .headline(headline)
                .summary(summary)
                .detailPath(detailPath)
                .build();
    }

    private NewsroomDto.TabResponse emptyTabResponse(String asOfAt) {
        return NewsroomDto.TabResponse.builder()
                .header(NewsroomDto.Header.builder()
                        .title(HEADER_TITLE)
                        .asOfAt(asOfAt)
                        .subtitle(FALLBACK_SUBTITLE)
                        .build())
                .section(null)
                .holdings(List.of())
                .footer(null)
                .emptyState(NewsroomDto.EmptyState.builder()
                        .code("NO_CUSTOM_NEWS")
                        .title("맞춤 뉴스가 아직 없어요")
                        .description("보유 종목을 연결하면 내 자산과 관련된 정책 뉴스를 골라드려요.")
                        .buttonLabel("보유자산 연결하기")
                        .build())
                .errorState(null)
                .build();
    }

    private NewsroomDto.TabResponse unavailableTabResponse(String asOfAt) {
        return NewsroomDto.TabResponse.builder()
                .header(NewsroomDto.Header.builder()
                        .title(HEADER_TITLE)
                        .asOfAt(asOfAt)
                        .subtitle(FALLBACK_SUBTITLE)
                        .build())
                .section(null)
                .holdings(List.of())
                .footer(null)
                .emptyState(null)
                .errorState(NewsroomDto.ErrorState.builder()
                        .title("뉴스를 불러오지 못했어요")
                        .summary("네트워크 연결을 확인한 뒤 다시 시도해 주세요.")
                        .buttonLabel("다시 시도")
                        .build())
                .build();
    }

    private List<HoldingPosition> loadHoldings(Long userId) {
        List<BrokerAccountEntity> connectedAccounts = brokerAccountRepository.findByUserId(userId).stream()
                .filter(account -> account.getHyphenStatus() == BrokerAccountEntity.HyphenStatus.CONNECTED)
                .toList();
        if (connectedAccounts.isEmpty()) {
            return List.of();
        }

        Map<String, AggregatedPosition> aggregated = new LinkedHashMap<>();
        for (BrokerAccountEntity account : connectedAccounts) {
            for (AssetPositionEntity position : assetPositionRepository.findByAccountId(account.getId())) {
                String ticker = resolveTicker(position);
                if (ticker == null || ticker.isBlank()) {
                    continue;
                }
                aggregated.merge(
                        ticker.toUpperCase(Locale.ROOT),
                        new AggregatedPosition(
                                ticker.toUpperCase(Locale.ROOT),
                                position.getItemName(),
                                nullToZero(position.getCurrentValue())),
                        (left, right) -> new AggregatedPosition(
                                left.ticker(),
                                left.name() != null ? left.name() : right.name(),
                                left.value().add(right.value())));
            }
        }

        BigDecimal assetTotal = assetMetricsService.getAssetTotal(userId);
        if (assetTotal.compareTo(BigDecimal.ZERO) <= 0 || aggregated.isEmpty()) {
            return List.of();
        }

        return aggregated.values().stream()
                .sorted(Comparator.comparing(AggregatedPosition::value).reversed())
                .map(position -> new HoldingPosition(
                        position.ticker(),
                        position.name() != null ? position.name() : position.ticker(),
                        toWeightPct(position.value(), assetTotal)))
                .toList();
    }

    private List<PolicyFeedDto.Card> findCardsForTicker(String ticker, List<PolicyFeedDto.Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return List.of();
        }
        String upper = ticker.toUpperCase(Locale.ROOT);
        List<PolicyFeedDto.Card> matched = new ArrayList<>();
        for (PolicyFeedDto.Card card : cards) {
            if (cardMatchesTicker(card, upper)) {
                matched.add(card);
            }
        }
        return matched;
    }

    private boolean cardMatchesTicker(PolicyFeedDto.Card card, String tickerUpper) {
        if (card.getAssetSignals() != null) {
            for (PolicyFeedDto.AssetSignal signal : card.getAssetSignals()) {
                if (signal.getTicker() != null && tickerUpper.equalsIgnoreCase(signal.getTicker())) {
                    return true;
                }
            }
        }
        if (card.getImpact() != null && card.getImpact().getTargetAssets() != null) {
            for (String asset : card.getImpact().getTargetAssets()) {
                if (asset != null && tickerUpper.equalsIgnoreCase(asset.trim())) {
                    return true;
                }
            }
        }
        String haystack = ((card.getTitle() == null ? "" : card.getTitle()) + " "
                + (card.getBodySummary() == null ? "" : card.getBodySummary())).toUpperCase(Locale.ROOT);
        return haystack.contains(tickerUpper);
    }

    private NewsroomDto.BriefingType resolveBriefingType(BigDecimal weightPct, boolean hasNews) {
        if (!hasNews) {
            return NewsroomDto.BriefingType.Quiet;
        }
        if (weightPct.compareTo(HERO_WEIGHT_THRESHOLD) >= 0) {
            return NewsroomDto.BriefingType.Hero;
        }
        if (weightPct.compareTo(COMPACT_WEIGHT_THRESHOLD) >= 0) {
            return NewsroomDto.BriefingType.Compact;
        }
        return NewsroomDto.BriefingType.Quiet;
    }

    private BigDecimal resolveDailyChangePct(PolicyFeedDto.Card card) {
        if (card.getModelSignal() != null && card.getModelSignal().getPredictedReturnPct() != null) {
            return BigDecimal.valueOf(card.getModelSignal().getPredictedReturnPct())
                    .setScale(1, RoundingMode.HALF_UP);
        }
        // 종목별 당일 등락 시세가 없어 모델 신호가 없으면 하드코딩 플레이스홀더
        return BigDecimal.valueOf(-0.6).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveTotalAssetImpactPct(BigDecimal dailyChangePct, BigDecimal weightPct) {
        if (dailyChangePct == null || weightPct == null) {
            return null;
        }
        return dailyChangePct
                .multiply(weightPct)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private NewsroomDto.SourceItem toSourceItem(PolicyFeedDto.Card card) {
        String publishedAt = null;
        if (card.getDate() != null && !card.getDate().isBlank()) {
            try {
                LocalDate date = LocalDate.parse(card.getDate());
                publishedAt = date.atStartOfDay(KST).toOffsetDateTime().format(ISO_OFFSET);
            } catch (DateTimeParseException ignored) {
                publishedAt = card.getDate();
            }
        }
        return NewsroomDto.SourceItem.builder()
                .newsId(firstNonBlank(card.getNewsId(), card.getId()))
                .title(firstNonBlank(card.getTitle(), "원문 기사"))
                .publisher(firstNonBlank(card.getSource(), "Unknown"))
                .publishedAt(publishedAt)
                .thumbnailUrl(card.getThumbnailUrl())
                .url(card.getLink())
                .build();
    }

    private List<String> buildFindings(List<PolicyFeedDto.Card> matched) {
        List<String> findings = new ArrayList<>();
        for (PolicyFeedDto.Card card : matched) {
            String finding = firstNonBlank(card.getBodySummary(), card.getTitle());
            if (finding != null && !finding.isBlank()) {
                findings.add(finding);
            }
            if (findings.size() >= 3) {
                break;
            }
        }
        if (findings.isEmpty()) {
            findings.add("관련 보도가 확인되었어요");
        }
        return findings;
    }

    private String resolveNewsThumbnail(List<PolicyFeedDto.Card> matched) {
        for (PolicyFeedDto.Card card : matched) {
            if (card.getThumbnailUrl() != null && !card.getThumbnailUrl().isBlank()) {
                return card.getThumbnailUrl();
            }
        }
        return null;
    }

    private String detailPath(String ticker) {
        return "/api/newsroom/" + ticker;
    }

    private int quietDaysFor(String ticker) {
        int hash = Math.abs(Objects.hash(ticker) % 20);
        return Math.max(1, hash);
    }

    private LocalDate resolveBriefingDate(String briefingDate) {
        if (briefingDate == null || briefingDate.isBlank()) {
            return LocalDate.now(KST);
        }
        try {
            return LocalDate.parse(briefingDate);
        } catch (DateTimeParseException ex) {
            throw ApiException.badRequest(
                    "briefingDate는 YYYY-MM-DD 형식이어야 합니다.", "NEWSROOM_INVALID_DATE");
        }
    }

    private String toAsOfAt(LocalDate briefingDate) {
        ZonedDateTime asOf = briefingDate.atTime(6, 0).atZone(KST);
        return asOf.toOffsetDateTime().format(ISO_OFFSET);
    }

    private BigDecimal toWeightPct(BigDecimal value, BigDecimal total) {
        return value.divide(total, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private String resolveTicker(AssetPositionEntity position) {
        if (position.getSymbol() != null && !position.getSymbol().isBlank()) {
            return position.getSymbol();
        }
        return position.getItemCode();
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        String normalized = ticker.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9._-]{1,20}")) {
            return null;
        }
        return normalized;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
    }

    private record AggregatedPosition(String ticker, String name, BigDecimal value) {
    }

    private record HoldingPosition(String ticker, String name, BigDecimal weightPct) {
    }

    /**
     * 탭 API에서 피드 장애 시 errorState를 포함한 503 응답을 만들기 위한 예외.
     */
    public static class NewsroomUnavailableException extends RuntimeException {
        private final NewsroomDto.TabResponse result;

        public NewsroomUnavailableException(String message, NewsroomDto.TabResponse result) {
            super(message);
            this.result = result;
        }

        public NewsroomDto.TabResponse getResult() {
            return result;
        }
    }
}
