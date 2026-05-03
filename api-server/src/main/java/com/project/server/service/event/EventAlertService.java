package com.project.server.service.event;

import com.project.server.domain.UserEventAlertEntity;
import com.project.server.exception.ApiException;
import com.project.server.repository.UserEventAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventAlertService {

    private final UserEventAlertRepository eventAlertRepository;

    @Transactional(readOnly = true)
    public boolean isEventAlertEnabled(Long userId, Long eventId) {
        return eventAlertRepository.findByUserIdAndEventId(userId, eventId)
                .map(UserEventAlertEntity::isEnabled)
                .orElse(false);
    }

    @Transactional
    public boolean upsertEventAlert(Long userId, Long eventId, boolean enabled) {
        UserEventAlertEntity current = eventAlertRepository.findByUserIdAndEventId(userId, eventId)
                .orElseGet(() -> UserEventAlertEntity.builder()
                        .userId(userId)
                        .eventId(eventId)
                        .enabled(enabled)
                        .build());

        current.setEnabled(enabled);
        
        try {
            UserEventAlertEntity saved = eventAlertRepository.save(current);
            
            // 저장된 값이 null이거나 요청한 값과 다르면 예외 발생
            if (saved == null) {
                throw ApiException.internalServerError(
                        "이벤트 알림 설정 저장에 실패했습니다.",
                        "EVENT_ALERT_SAVE_FAILED");
            }
            
            if (saved.isEnabled() != enabled) {
                throw ApiException.internalServerError(
                        "이벤트 알림 설정값이 올바르게 저장되지 않았습니다.",
                        "EVENT_ALERT_MISMATCH");
            }
            
            return saved.isEnabled();
        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw e;
            }
            throw ApiException.internalServerError(
                    "이벤트 알림 설정 중 오류가 발생했습니다: " + e.getMessage(),
                    "EVENT_ALERT_UPDATE_ERROR");
        }
    }
}
