package com.project.server.service.event;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EventScheduleService {

    private static final List<EventSchedule> UPDATED_EVENTS = List.of(
            new EventSchedule("04:00", "미국 ISM 제조업 지수", "발표까지 31분", List.of("나스닥 성장주 ETF", "달러 인덱스 ETF"), "예정"),
            new EventSchedule("08:30", "미국 신규 실업수당 청구", "발표까지 31분", List.of("달러 인덱스 ETF"), "예정"),
            new EventSchedule("21:30", "중국 제조업 PMI", "발표까지 31분", List.of("코스피 ETF"), "예정")
    );

    private final Map<Long, Integer> userEventIndex = new ConcurrentHashMap<>();

    public EventSchedule getCurrentEvent(Long userId) {
        int idx = userEventIndex.getOrDefault(userId, 0);
        return UPDATED_EVENTS.get(idx % UPDATED_EVENTS.size());
    }

    public EventSchedule refreshEvent(Long userId) {
        int next = (userEventIndex.getOrDefault(userId, 0) + 1) % UPDATED_EVENTS.size();
        userEventIndex.put(userId, next);
        return UPDATED_EVENTS.get(next);
    }

    public record EventSchedule(String timeText, String title, String countdownText, List<String> relatedAssets, String statusText) {}
}
