package com.project.server.service.event;

import com.project.server.dto.EventDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventAlertService eventAlertService;
    private final EventScheduleService eventScheduleService;

    public EventDto.EventsResponse getEvents(Long userId, String dateSegment, String category) {
        EventScheduleService.EventSchedule event = eventScheduleService.getCurrentEvent(userId);
        boolean alertEnabled = eventAlertService.isEventAlertEnabled(userId, event.eventId());
        int importanceStars = "발표완료".equals(event.statusText()) ? 3 : 4;

        return EventDto.EventsResponse.builder()
            .dateSegments(List.of("today", "tomorrow", "this_week", "past"))
            .categories(List.of("all", "rate", "inflation", "employment", "fx", "speech"))
            .items(List.of(
                EventDto.EventItem.builder()
                    .eventId(event.eventId())
                    .timeText(event.timeText())
                    .title(event.title())
                    .statusText(event.statusText())
                    .tags(List.of("정책"))
                    .importanceStars(importanceStars)
                    .countdownText(event.countdownText())
                    .relatedAssets(event.relatedAssets())
                    .alertEnabled(alertEnabled)
                    .build()
            ))
            .build();
    }

    public EventDto.EventsResponse refreshEvents(Long userId, String dateSegment, String category) {
        eventScheduleService.refreshEvent(userId);
        return getEvents(userId, dateSegment, category);
    }

    public EventDto.EventAlertResponse updateEventAlert(Long userId, Long eventId, boolean enabled) {
        boolean storedEnabled = eventAlertService.upsertEventAlert(userId, eventId, enabled);
        return EventDto.EventAlertResponse.builder()
                .eventId(eventId)
                .enabled(storedEnabled)
                .build();
    }
}
