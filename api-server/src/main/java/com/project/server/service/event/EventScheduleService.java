package com.project.server.service.event;

import com.project.server.domain.PolicyEventEntity;
import com.project.server.domain.UserEventCursorEntity;
import com.project.server.domain.UserWatchAssetEntity;
import com.project.server.exception.ApiException;
import com.project.server.repository.PolicyEventJpaRepository;
import com.project.server.repository.UserEventCursorRepository;
import com.project.server.repository.UserWatchAssetRepository;
import com.project.server.repository.UserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventScheduleService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final PolicyEventJpaRepository policyEventJpaRepository;
    private final UserEventCursorRepository userEventCursorRepository;
    private final UserWatchAssetRepository userWatchAssetRepository;
    private final UserJpaRepository userJpaRepository;

    @Transactional(readOnly = true)
    public EventSchedule getCurrentEvent(Long userId) {
        validateUser(userId);
        List<PolicyEventEntity> events = policyEventJpaRepository.findTop20ByOrderByCreatedAtDesc();
        if (events.isEmpty()) {
            return new EventSchedule(
                    0L,
                    "--:--",
                    "정책 이벤트 데이터 대기",
                    "발표 일정 대기",
                    List.of("자산 정보 대기"),
                    "대기"
            );
        }

        int cursor = userEventCursorRepository.findByUserId(userId)
                .map(UserEventCursorEntity::getCursorIndex)
                .orElse(0);
        PolicyEventEntity selected = events.get(Math.floorMod(cursor, events.size()));

        LocalDateTime scheduledAt = (selected.getCreatedAt() == null ? LocalDateTime.now() : selected.getCreatedAt()).plusHours(24);
        return new EventSchedule(
                selected.getId(),
                scheduledAt.format(TIME_FORMATTER),
                selected.getTitle(),
                buildCountdownText(scheduledAt),
                resolveRelatedAssets(userId),
                scheduledAt.isAfter(LocalDateTime.now()) ? "예정" : "발표완료"
        );
    }

    @Transactional
    public EventSchedule refreshEvent(Long userId) {
        validateUser(userId);
        UserEventCursorEntity cursor = userEventCursorRepository.findByUserId(userId)
                .orElseGet(() -> UserEventCursorEntity.builder().userId(userId).cursorIndex(0).build());
        cursor.setCursorIndex(cursor.getCursorIndex() + 1);
        userEventCursorRepository.save(cursor);
        return getCurrentEvent(userId);
    }

    private void validateUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("userId는 양수여야 합니다.", "AI_TRIGGER_INVALID_USER_ID");
        }
        if (!userJpaRepository.existsById(userId)) {
            throw ApiException.notFound("존재하지 않는 사용자입니다.", "AI_TRIGGER_USER_NOT_FOUND");
        }
    }

    private String buildCountdownText(LocalDateTime targetTime) {
        Duration duration = Duration.between(LocalDateTime.now(), targetTime);
        if (duration.isNegative()) {
            return "발표됨";
        }
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        return "발표까지 " + hours + "시간 " + minutes + "분";
    }

    private List<String> resolveRelatedAssets(Long userId) {
        List<UserWatchAssetEntity> assets = userWatchAssetRepository.findByUserIdOrderByDisplayOrderAsc(userId);
        if (assets.isEmpty()) {
            return List.of("관심 자산 미설정");
        }
        return assets.stream().map(UserWatchAssetEntity::getAssetName).limit(3).toList();
    }

    public record EventSchedule(
            Long eventId,
            String timeText,
            String title,
            String countdownText,
            List<String> relatedAssets,
            String statusText
    ) {}
}
