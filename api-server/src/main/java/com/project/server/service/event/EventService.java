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

    public List<String> getDateSegments() {
        return List.of("today", "tomorrow", "this_week", "past");
    }

    public List<String> getCategories() {
        return List.of("all", "rate", "inflation", "employment", "fx", "speech");
    }

    public List<EventDto.EventItem> getEventItems(Long userId, String dateSegment, String category) {
        return List.of(buildCurrentEventItem(userId));
    }

    public EventDto.EventsResponse getEvents(Long userId, String dateSegment, String category) {
        return EventDto.EventsResponse.builder()
            .dateSegments(getDateSegments())
            .categories(getCategories())
            .items(getEventItems(userId, dateSegment, category))
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

    private EventDto.EventItem buildCurrentEventItem(Long userId) {
        EventScheduleService.EventSchedule event = eventScheduleService.getCurrentEvent(userId);
        boolean alertEnabled = eventAlertService.isEventAlertEnabled(userId, event.eventId());
        int importanceStars = "발표완료".equals(event.statusText()) ? 3 : 4;

        return EventDto.EventItem.builder()
                .eventId(event.eventId())
                .timeText(event.timeText())
                .title(event.title())
                .statusText(event.statusText())
                .tags(List.of("정책"))
                .importanceStars(importanceStars)
                .countdownText(event.countdownText())
                .relatedAssets(event.relatedAssets())
                .alertEnabled(alertEnabled)
                .build();
    }
}
