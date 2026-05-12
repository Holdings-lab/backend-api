package com.project.server.service.integration;

import com.project.server.dto.ActionDto;
import com.project.server.dto.PolicyFeedDto;
import com.project.server.exception.ApiException;
import com.project.server.service.event.EventScheduleService;
import com.project.server.service.home.FeaturedEventStateService;
import com.project.server.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AiPipelineTriggerService {

    private final EventScheduleService eventScheduleService;
    private final FeaturedEventStateService featuredEventStateService;
    private final PolicyFeedProxyService policyFeedProxyService;
    private final UserJpaRepository userJpaRepository;

    public ActionDto.ActionResponse triggerAndUpdateFeatured(Long userId) {
        validateUser(userId);

        PolicyFeedDto.PolicyFeedResponse policyFeed = policyFeedProxyService.getPolicyFeed(userId, 10, "all", "", "");
        EventScheduleService.EventSchedule current = eventScheduleService.getCurrentEvent(userId);
        List<String> dynamicTags = buildDynamicTags(current, policyFeed);
        String featuredTitle = resolveFeaturedTitle(current, policyFeed);
        String featuredSummary = resolveFeaturedSummary(current, policyFeed);

        featuredEventStateService.setFeatured(
                userId,
                featuredTitle,
                featuredSummary,
                current.countdownText(),
                dynamicTags
        );

        return ActionDto.ActionResponse.builder()
                .action("ai-trigger")
                .status("completed")
                .userId(userId)
                .eventId(current.eventId())
                .featuredTitle(featuredTitle)
                .featuredSummary(featuredSummary)
                .countdownText(current.countdownText())
                .tags(dynamicTags)
                .policyFeedCardCount(policyFeed.getCards() == null ? 0 : policyFeed.getCards().size())
                .build();
    }

    private void validateUser(Long userId) {
        if (userId == null) {
            throw ApiException.badRequest("userId는 필수 파라미터입니다.", "AI_TRIGGER_INVALID_USER_ID");
        }
        if (userId <= 0) {
            throw ApiException.badRequest("userId는 양수여야 합니다.", "AI_TRIGGER_INVALID_USER_ID");
        }
        if (!userJpaRepository.existsById(userId)) {
            throw ApiException.notFound("존재하지 않는 사용자입니다.", "AI_TRIGGER_USER_NOT_FOUND");
        }
    }

    private List<String> buildDynamicTags(EventScheduleService.EventSchedule current,
            PolicyFeedDto.PolicyFeedResponse policyFeed) {
        Set<String> tags = new LinkedHashSet<>();

        addTags(tags, current.relatedAssets());

        if (policyFeed != null) {
            if (policyFeed.getSummary() != null && policyFeed.getSummary().getTopCategories() != null) {
                for (PolicyFeedDto.CategoryCount category : policyFeed.getSummary().getTopCategories()) {
                    addTag(tags, category == null ? null : category.getCategory());
                }
            }

            if (policyFeed.getCards() != null) {
                policyFeed.getCards().stream()
                        .limit(3)
                        .forEach(card -> {
                            if (card != null) {
                                addTag(tags, card.getCategory());
                                addTag(tags, card.getDocType());
                                addTags(tags, card.getTags());
                            }
                        });
            }
        }

        if (tags.isEmpty()) {
            addTag(tags, current.title());
        }

        return new ArrayList<>(tags).stream().limit(5).toList();
    }

    private String resolveFeaturedTitle(EventScheduleService.EventSchedule current,
            PolicyFeedDto.PolicyFeedResponse policyFeed) {
        if (current.title() != null && !current.title().isBlank()) {
            return current.title();
        }
        if (policyFeed != null && policyFeed.getSummary() != null
                && policyFeed.getSummary().getOverallSentiment() != null
                && !policyFeed.getSummary().getOverallSentiment().isBlank()) {
            return policyFeed.getSummary().getOverallSentiment();
        }
        return "추천 피드";
    }

    private String resolveFeaturedSummary(EventScheduleService.EventSchedule current,
            PolicyFeedDto.PolicyFeedResponse policyFeed) {
        List<String> parts = new ArrayList<>();
        if (current.countdownText() != null && !current.countdownText().isBlank()) {
            parts.add(current.countdownText());
        }
        if (policyFeed != null && policyFeed.getSummary() != null) {
            if (policyFeed.getSummary().getOverallSentiment() != null
                    && !policyFeed.getSummary().getOverallSentiment().isBlank()) {
                parts.add(policyFeed.getSummary().getOverallSentiment());
            }
            if (policyFeed.getSummary().getTopCategories() != null && !policyFeed.getSummary().getTopCategories().isEmpty()) {
                String topCategory = policyFeed.getSummary().getTopCategories().get(0).getCategory();
                if (topCategory != null && !topCategory.isBlank()) {
                    parts.add(topCategory);
                }
            }
        }
        if (parts.isEmpty()) {
            parts.add("AI 파이프라인 실행 완료");
        }
        return String.join(" · ", parts);
    }

    private void addTags(Set<String> tags, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            addTag(tags, value);
        }
    }

    private void addTag(Set<String> tags, String value) {
        if (value == null) {
            return;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty() && !isPlaceholderTag(normalized)) {
            tags.add(normalized);
        }
    }

    private boolean isPlaceholderTag(String value) {
        return "관심 자산 미설정".equals(value) || "자산 정보 대기".equals(value) || "대기".equals(value);
    }
}
