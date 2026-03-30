package com.project.server.service.event;

import com.project.server.domain.UserEventAlertEntity;
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
        eventAlertRepository.save(current);
        return current.isEnabled();
    }
}
