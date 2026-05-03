package com.project.server.service.event;

import com.project.server.dto.EventDto;
import com.project.server.dto.PolicyFeedDto;
import com.project.server.domain.UserWatchAssetEntity;
import com.project.server.exception.ApiException;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.UserWatchAssetRepository;
import com.project.server.service.integration.PolicyFeedProxyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventAlertService eventAlertService;
    private final PolicyFeedProxyService policyFeedProxyService;
    private final UserJpaRepository userJpaRepository;
    private final UserWatchAssetRepository userWatchAssetRepository;

    public List<String> getDateSegments(Long userId) {
        ensureUserExists(userId);
        return loadEventFeed(userId).dateSegments();
    }

    public List<String> getCategories(Long userId) {
        ensureUserExists(userId);
        return loadEventFeed(userId).categories();
    }

    public List<EventDto.EventItem> getEventItems(Long userId, String dateSegment, String category) {
        ensureUserExists(userId);
        validateDateSegment(dateSegment);
        validateCategory(category);
        
        List<String> dateSegments = parseSegments(dateSegment);
        List<String> categories = parseCategories(category);
        
        EventFeedSnapshot snapshot = loadEventFeed(userId);
        return snapshot.cards().stream()
                .filter(card -> matchesDateSegment(card, dateSegments))
                .filter(card -> matchesCategory(card, categories))
                .map(card -> toEventItem(userId, card))
                .toList();
    }

    public EventDto.EventsResponse getEvents(Long userId, String dateSegment, String category) {
        ensureUserExists(userId);
        validateDateSegment(dateSegment);
        validateCategory(category);
        
        List<String> dateSegments = parseSegments(dateSegment);
        List<String> categories = parseCategories(category);
        
        EventFeedSnapshot snapshot = loadEventFeed(userId);
        List<PolicyFeedDto.Card> filteredCards = snapshot.cards().stream()
                .filter(card -> matchesDateSegment(card, dateSegments))
                .filter(card -> matchesCategory(card, categories))
                .toList();
        
        List<EventDto.EventItem> items = filteredCards.stream()
                .map(card -> toEventItem(userId, card))
                .toList();
        
        // 필터링된 데이터에서만 dateSegments와 categories 추출
        List<String> filteredDateSegments = extractDateSegments(filteredCards);
        List<String> filteredCategories = extractCategories(filteredCards);
        
        return EventDto.EventsResponse.builder()
                .dateSegments(filteredDateSegments)
                .categories(filteredCategories)
                .items(items)
                .build();
    }

    public EventDto.EventAlertResponse updateEventAlert(Long userId, Long eventId, boolean enabled) {
        ensureUserExists(userId);
        if (eventId == null || eventId <= 0) {
            throw ApiException.badRequest("eventId는 양수여야 합니다.", "EVENT_INVALID_ID");
        }
        loadEventFeed(userId).cards().stream()
                .filter(card -> resolveEventId(card).equals(eventId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("정책 이벤트를 찾을 수 없습니다.", "EVENT_NOT_FOUND"));
        
        boolean storedEnabled = eventAlertService.upsertEventAlert(userId, eventId, enabled);
        
        // 저장 후 요청한 값과 실제 저장된 값이 일치하는지 검증
        if (storedEnabled != enabled) {
            throw ApiException.internalServerError(
                    "이벤트 알림 설정이 정상적으로 적용되지 않았습니다.",
                    "EVENT_ALERT_SAVE_VERIFICATION_FAILED");
        }
        
        return EventDto.EventAlertResponse.builder()
                .eventId(eventId)
                .enabled(storedEnabled)
                .build();
    }

    public EventDto.RelatedPoliciesResponse getRelatedPoliciesByAsset(Long userId, String assetName,
            String dateSegment, String category) {
        ensureUserExists(userId);
        validateDateSegment(dateSegment);
        validateCategory(category);
        
        if (assetName == null || assetName.isBlank()) {
            throw ApiException.badRequest("assetName은 필수입니다.", "EVENT_ASSET_NAME_REQUIRED");
        }

        Set<String> userAssets = loadUserAssetNames(userId);
        if (!userAssets.contains(assetName.trim())) {
            throw ApiException.badRequest("해당 사용자의 관심 자산이 아닙니다.", "EVENT_ASSET_NOT_SELECTED");
        }

        List<String> dateSegments = parseSegments(dateSegment);
        List<String> categories = parseCategories(category);

        EventFeedSnapshot snapshot = loadEventFeed(userId);
        List<EventDto.RelatedPolicyItem> items = snapshot.cards().stream()
                .filter(card -> matchesDateSegment(card, dateSegments))
                .filter(card -> matchesCategory(card, categories))
                .map(card -> buildRelatedPolicyItem(assetName.trim(), card))
                .filter(item -> item.getRelevanceScore() >= 45)
                .sorted(Comparator.comparingInt(EventDto.RelatedPolicyItem::getRelevanceScore).reversed())
                .toList();

        return EventDto.RelatedPoliciesResponse.builder()
                .userId(userId)
                .assetName(assetName.trim())
                .policies(items)
                .build();
    }

    public EventDto.RelatedAssetsResponse getRelatedAssetsByPolicy(Long userId, Long eventId) {
        ensureUserExists(userId);
        if (eventId == null || eventId <= 0) {
            throw ApiException.badRequest("eventId는 양수여야 합니다.", "EVENT_INVALID_ID");
        }

        EventFeedSnapshot snapshot = loadEventFeed(userId);
        PolicyFeedDto.Card target = snapshot.cards().stream()
                .filter(card -> resolveEventId(card).equals(eventId))
                .findFirst()
                .orElseThrow(() -> ApiException.notFound("정책 이벤트를 찾을 수 없습니다.", "EVENT_NOT_FOUND"));

        List<EventDto.RelatedAssetItem> assets = loadUserAssets(userId).stream()
                .map(asset -> buildRelatedAssetItem(asset.getAssetName(), target))
                .filter(item -> item.getRelevanceScore() >= 45)
                .sorted(Comparator.comparingInt(EventDto.RelatedAssetItem::getRelevanceScore).reversed())
                .toList();

        return EventDto.RelatedAssetsResponse.builder()
                .userId(userId)
                .eventId(eventId)
                .policyTitle(resolveTitle(target))
                .assets(assets)
                .build();
    }

    private EventDto.EventItem toEventItem(Long userId, PolicyFeedDto.Card card) {
        Long eventId = resolveEventId(card);
        boolean alertEnabled = eventAlertService.isEventAlertEnabled(userId, eventId);

        return EventDto.EventItem.builder()
                .eventId(eventId)
                .timeText(resolveTimeText(card))
                .title(resolveTitle(card))
                .statusText(resolveStatusText(card))
                .tags(resolveTags(card))
                .importanceStars(resolveImportanceStars(card))
                .countdownText(resolveCountdownText(card))
                .relatedAssets(resolveRelatedAssets(card))
                .alertEnabled(alertEnabled)
                .build();
    }

    private EventFeedSnapshot loadEventFeed(Long userId) {
        try {
            PolicyFeedDto.PolicyFeedRequest request = PolicyFeedDto.PolicyFeedRequest.builder()
                    .userId(userId)
                    .build();

            PolicyFeedDto.PolicyFeedResponse response = policyFeedProxyService.getPolicyFeed(request);
            List<PolicyFeedDto.Card> cards = response == null || response.getCards() == null
                    ? List.of()
                    : response.getCards();

            return new EventFeedSnapshot(
                    cards,
                    resolveDateSegments(cards),
                    resolveCategories(response, cards));
        } catch (Exception e) {
            throw ApiException.internalServerError("정책 피드를 불러오는 중 오류가 발생했습니다.", "EVENT_FEED_LOAD_ERROR");
        }
    }

    private List<UserWatchAssetEntity> loadUserAssets(Long userId) {
        return userWatchAssetRepository.findByUserIdOrderByDisplayOrderAsc(userId);
    }

    private Set<String> loadUserAssetNames(Long userId) {
        return loadUserAssets(userId).stream().map(UserWatchAssetEntity::getAssetName).collect(java.util.stream.Collectors.toSet());
    }

    private List<String> resolveDateSegments(List<PolicyFeedDto.Card> cards) {
        Set<String> segments = new LinkedHashSet<>();
        for (PolicyFeedDto.Card card : cards) {
            String segment = resolveDateSegment(card.getDate());
            if (segment != null) {
                segments.add(segment);
            }
        }
        return new ArrayList<>(segments);
    }

    private List<String> resolveCategories(PolicyFeedDto.PolicyFeedResponse response, List<PolicyFeedDto.Card> cards) {
        Set<String> categories = new LinkedHashSet<>();

        if (response != null && response.getFilters() != null && response.getFilters().getCategories() != null) {
            for (String category : response.getFilters().getCategories()) {
                if (category != null && !category.isBlank()) {
                    categories.add(category);
                }
            }
        }

        if (categories.isEmpty()) {
            for (PolicyFeedDto.Card card : cards) {
                if (card.getCategory() != null && !card.getCategory().isBlank()) {
                    categories.add(card.getCategory());
                }
            }
        }

        return new ArrayList<>(categories);
    }

    private boolean matchesDateSegment(PolicyFeedDto.Card card, List<String> dateSegments) {
        if (dateSegments.contains("all")) {
            return true;
        }
        String resolvedSegment = resolveDateSegment(card.getDate());
        return resolvedSegment != null && dateSegments.stream()
                .anyMatch(seg -> seg.equalsIgnoreCase(resolvedSegment));
    }

    private boolean matchesCategory(PolicyFeedDto.Card card, List<String> categories) {
        if (categories.contains("all")) {
            return true;
        }

        for (String category : categories) {
            String normalizedCategory = normalize(category);
            if (normalize(card.getCategory()).equals(normalizedCategory)
                    || normalize(card.getDocType()).equals(normalizedCategory)
                    || card.getTags() != null && card.getTags().stream()
                            .filter(Objects::nonNull)
                            .map(this::normalize)
                            .anyMatch(normalizedCategory::equals)
                    || resolveStatusText(card).equalsIgnoreCase(normalizedCategory)) {
                return true;
            }
        }
        return false;
    }

    private String resolveTitle(PolicyFeedDto.Card card) {
        if (card.getTitle() != null && !card.getTitle().isBlank()) {
            return card.getTitle();
        }
        if (card.getBodySummary() != null && !card.getBodySummary().isBlank()) {
            return card.getBodySummary();
        }
        return "정책 이벤트";
    }

    private String resolveStatusText(PolicyFeedDto.Card card) {
        if (card.getModelSignal() != null && card.getModelSignal().getSignal() != null
                && !card.getModelSignal().getSignal().isBlank()) {
            return card.getModelSignal().getSignal();
        }
        if (card.getCategory() != null && !card.getCategory().isBlank()) {
            return card.getCategory();
        }
        if (card.getDocType() != null && !card.getDocType().isBlank()) {
            return card.getDocType();
        }
        return "unknown";
    }

    private EventDto.RelatedPolicyItem buildRelatedPolicyItem(String assetName, PolicyFeedDto.Card card) {
        int relevanceScore = estimateRelevanceScore(assetName, card);
        String direction = resolveDirection(card);
        int volatilityScore = estimateVolatilityScore(card);

        return EventDto.RelatedPolicyItem.builder()
                .eventId(resolveEventId(card))
                .title(resolveTitle(card))
                .category(card.getCategory())
                .date(card.getDate())
                .direction(direction)
                .volatilityScore(volatilityScore)
                .relevanceScore(relevanceScore)
                .reason(buildRelationReason(assetName, card, relevanceScore, direction))
                .build();
    }

    private EventDto.RelatedAssetItem buildRelatedAssetItem(String assetName, PolicyFeedDto.Card card) {
        int relevanceScore = estimateRelevanceScore(assetName, card);
        String direction = resolveDirection(card);
        int volatilityScore = estimateVolatilityScore(card);

        return EventDto.RelatedAssetItem.builder()
                .assetName(assetName)
                .direction(direction)
                .volatilityScore(volatilityScore)
                .relevanceScore(relevanceScore)
                .reason(buildRelationReason(assetName, card, relevanceScore, direction))
                .build();
    }

    private int estimateRelevanceScore(String assetName, PolicyFeedDto.Card card) {
        Set<String> policyThemes = detectPolicyThemes(card);
        Set<String> assetThemes = detectAssetThemes(assetName);

        int overlap = 0;
        for (String theme : assetThemes) {
            if (policyThemes.contains(theme)) {
                overlap++;
            }
        }

        int score = overlap * 35;
        score += Math.round(resolveConfidence(card) * 25f);
        score += Math.round(Math.min(1.0, Math.abs(resolveSentiment(card))) * 20f);
        score += Math.round(Math.min(1.0, resolveModelMove(card)) * 20f);
        return Math.max(0, Math.min(100, score));
    }

    private Set<String> detectPolicyThemes(PolicyFeedDto.Card card) {
        Set<String> themes = new HashSet<>();
        String text = (safe(card.getCategory()) + " " + safe(card.getDocType()) + " " + safe(card.getTitle()) + " "
                + safe(card.getBodySummary())).toLowerCase();

        if (containsAny(text, "fomc", "금리", "rate", "yield", "bis", "bond", "채권")) {
            themes.add("rate");
        }
        if (containsAny(text, "fx", "환율", "달러", "dollar")) {
            themes.add("dollar");
        }
        if (containsAny(text, "semiconductor", "반도체", "chip", "tech", "nasdaq")) {
            themes.add("growth");
        }
        if (containsAny(text, "inflation", "cpi", "pce", "물가")) {
            themes.add("inflation");
        }
        if (containsAny(text, "employment", "labor", "고용")) {
            themes.add("employment");
        }
        if (containsAny(text, "white house", "fiscal", "infrastructure", "정책")) {
            themes.add("policy");
        }

        return themes;
    }

    private Set<String> detectAssetThemes(String assetName) {
        Set<String> themes = new HashSet<>();
        String text = safe(assetName).toLowerCase();

        if (containsAny(text, "장기채", "채", "bond")) {
            themes.add("rate");
        }
        if (containsAny(text, "달러", "dollar", "fx")) {
            themes.add("dollar");
        }
        if (containsAny(text, "나스닥", "성장주", "tech", "비트코인", "코스피")) {
            themes.add("growth");
            themes.add("policy");
        }
        if (containsAny(text, "금 etf", "gold", "금 ")) {
            themes.add("inflation");
            themes.add("dollar");
        }
        return themes;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private double resolveConfidence(PolicyFeedDto.Card card) {
        if (card.getModelSignal() == null || card.getModelSignal().getConfidence() == null) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, card.getModelSignal().getConfidence()));
    }

    private double resolveSentiment(PolicyFeedDto.Card card) {
        if (card.getSentiment() == null || card.getSentiment().getBodySentimentScore() == null) {
            return 0.0;
        }
        return card.getSentiment().getBodySentimentScore();
    }

    private double resolveModelMove(PolicyFeedDto.Card card) {
        if (card.getModelSignal() == null || card.getModelSignal().getPredictedReturnPct() == null) {
            return 0.0;
        }
        return Math.abs(card.getModelSignal().getPredictedReturnPct()) / 5.0;
    }

    private String resolveDirection(PolicyFeedDto.Card card) {
        if (card.getModelSignal() == null || card.getModelSignal().getSignal() == null) {
            return "중립";
        }

        String signal = card.getModelSignal().getSignal().trim().toLowerCase();
        if ("buy".equals(signal)) {
            return "상방";
        }
        if ("sell".equals(signal)) {
            return "하방";
        }
        return "중립";
    }

    private int estimateVolatilityScore(PolicyFeedDto.Card card) {
        int sentimentVol = (int) Math.round(Math.min(1.0, Math.abs(resolveSentiment(card))) * 100.0);
        int modelVol = (int) Math.round(Math.min(1.0, resolveModelMove(card)) * 100.0);
        return Math.max(0, Math.min(100, Math.max(sentimentVol, modelVol)));
    }

    private String buildRelationReason(String assetName, PolicyFeedDto.Card card, int relevanceScore, String direction) {
        return assetName + "와(과) 정책 키워드(" + safe(card.getCategory()) + ")의 주제 연관도 및 모델 신호("
                + direction + ")를 합산해 " + relevanceScore + "점으로 산정되었습니다.";
    }

    private List<String> resolveTags(PolicyFeedDto.Card card) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        addIfPresent(tags, card.getCategory());
        addIfPresent(tags, card.getDocType());

        if (card.getTags() != null) {
            for (String tag : card.getTags()) {
                addIfPresent(tags, tag);
            }
        }

        if (card.getModelSignal() != null) {
            addIfPresent(tags, card.getModelSignal().getSignal());
        }

        if (card.getImpact() != null) {
            addIfPresent(tags, card.getImpact().getLabel());
            if (card.getImpact().getTargetAssets() != null) {
                for (String targetAsset : card.getImpact().getTargetAssets()) {
                    addIfPresent(tags, targetAsset);
                }
            }
        }

        return new ArrayList<>(tags);
    }

    private int resolveImportanceStars(PolicyFeedDto.Card card) {
        Integer impactScore = card.getImpact() == null ? null : card.getImpact().getScore();
        if (impactScore != null) {
            return clampStars((int) Math.round(impactScore / 20.0));
        }

        Double confidence = card.getModelSignal() == null ? null : card.getModelSignal().getConfidence();
        if (confidence != null) {
            return clampStars((int) Math.round(confidence * 5.0));
        }

        Double sentimentScore = card.getSentiment() == null ? null : card.getSentiment().getBodySentimentScore();
        if (sentimentScore != null) {
            return clampStars((int) Math.round(Math.abs(sentimentScore) * 5.0));
        }

        return 3;
    }

    private List<String> resolveRelatedAssets(PolicyFeedDto.Card card) {
        if (card.getImpact() == null || card.getImpact().getTargetAssets() == null) {
            return List.of();
        }
        return card.getImpact().getTargetAssets().stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String resolveTimeText(PolicyFeedDto.Card card) {
        if (card.getDate() == null || card.getDate().isBlank()) {
            return "--";
        }
        return card.getDate();
    }

    private String resolveCountdownText(PolicyFeedDto.Card card) {
        LocalDate date = parseDate(card.getDate());
        if (date == null) {
            return "--";
        }

        long diffDays = date.toEpochDay() - LocalDate.now().toEpochDay();
        if (diffDays == 0) {
            return "오늘";
        }
        if (diffDays > 0) {
            return "D-" + diffDays;
        }
        return "D+" + Math.abs(diffDays);
    }

    private String resolveDateSegment(String dateText) {
        LocalDate date = parseDate(dateText);
        if (date == null) {
            return null;
        }

        long diffDays = date.toEpochDay() - LocalDate.now().toEpochDay();
        
        // 미래 데이터는 제외 (발표된 정책만 필요)
        if (diffDays > 0) {
            return null;
        }
        
        // 과거 데이터 세분화
        if (diffDays == 0) {
            return "today";
        }
        if (diffDays == -1) {
            return "yesterday";
        }
        if (diffDays > -7) {  // -6 to -1 (yesterday 제외)
            return "this_week";
        }
        if (diffDays >= -14) {  // -14 to -7
            return "last_week";
        }
        return "older";  // diffDays < -14
    }

    private LocalDate parseDate(String dateText) {
        if (dateText == null || dateText.isBlank()) {
            return null;
        }

        try {
            return LocalDate.parse(dateText.trim());
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private Long resolveEventId(PolicyFeedDto.Card card) {
        if (card.getId() == null || card.getId().isBlank()) {
            return Math.abs((long) Objects.hash(card.getTitle(), card.getDate(), card.getCategory()));
        }

        String candidate = card.getId().trim();
        int lastDash = candidate.lastIndexOf('-');
        if (lastDash >= 0 && lastDash < candidate.length() - 1) {
            String suffix = candidate.substring(lastDash + 1);
            try {
                long parsed = Long.parseLong(suffix);
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return Math.abs((long) candidate.hashCode());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isAll(String value) {
        return value == null || value.isBlank() || "all".equalsIgnoreCase(value.trim());
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private void ensureUserExists(Long userId) {
        if (userId == null || !userJpaRepository.existsById(userId)) {
            throw ApiException.notFound("존재하지 않는 사용자입니다.", "EVENT_USER_NOT_FOUND");
        }
    }

    private void validateDateSegment(String dateSegment) {
        if (dateSegment == null) {
            throw ApiException.badRequest("dateSegment는 null이 될 수 없습니다.", "EVENT_DATESEGMENT_NULL");
        }
        String trimmed = dateSegment.trim();
        if (trimmed.isBlank()) {
            throw ApiException.badRequest("dateSegment는 비어 있을 수 없습니다.", "EVENT_DATESEGMENT_EMPTY");
        }
        if (isAll(trimmed)) {
            return;
        }

        for (String segment : trimmed.split(",")) {
            String normalized = segment.trim().toLowerCase();
            if (normalized.isBlank() || !isValidDateSegment(normalized)) {
                throw ApiException.badRequest(
                        "유효하지 않은 dateSegment입니다. 허용 값: all, today, yesterday, this_week, last_week, older",
                        "EVENT_INVALID_DATESEGMENT");
            }
        }
    }

    private void validateCategory(String category) {
        if (category == null) {
            throw ApiException.badRequest("category는 null이 될 수 없습니다.", "EVENT_CATEGORY_NULL");
        }
    }

    private boolean isValidDateSegment(String segment) {
        return "today".equals(segment)
                || "yesterday".equals(segment)
                || "this_week".equals(segment)
                || "last_week".equals(segment)
                || "older".equals(segment);
    }

    private int clampStars(int value) {
        return Math.max(1, Math.min(5, value));
    }

    private List<String> parseSegments(String dateSegmentParam) {
        if (dateSegmentParam == null || dateSegmentParam.isBlank()) {
            return List.of("all");
        }
        String trimmed = dateSegmentParam.trim();
        if ("all".equalsIgnoreCase(trimmed)) {
            return List.of("all");
        }
        List<String> segments = java.util.Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        if (segments.stream().anyMatch(segment -> "all".equalsIgnoreCase(segment))) {
            return List.of("all");
        }
        return segments;
    }

    private List<String> parseCategories(String categoryParam) {
        if (categoryParam == null || categoryParam.isBlank()) {
            return List.of("all");
        }
        String trimmed = categoryParam.trim();
        if ("all".equalsIgnoreCase(trimmed)) {
            return List.of("all");
        }
        return java.util.Arrays.stream(trimmed.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private List<String> extractDateSegments(List<PolicyFeedDto.Card> cards) {
        Set<String> segments = new LinkedHashSet<>();
        for (PolicyFeedDto.Card card : cards) {
            String segment = resolveDateSegment(card.getDate());
            if (segment != null) {
                segments.add(segment);
            }
        }
        return new ArrayList<>(segments);
    }

    private List<String> extractCategories(List<PolicyFeedDto.Card> cards) {
        Set<String> categories = new LinkedHashSet<>();
        for (PolicyFeedDto.Card card : cards) {
            if (card.getCategory() != null && !card.getCategory().isBlank()) {
                categories.add(card.getCategory());
            }
        }
        return new ArrayList<>(categories);
    }

    private record EventFeedSnapshot(
            List<PolicyFeedDto.Card> cards,
            List<String> dateSegments,
            List<String> categories) {
    }
}
