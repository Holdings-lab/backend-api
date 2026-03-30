package com.project.server.service.home;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FeaturedEventStateService {

    private final Map<Long, FeaturedEventState> userFeatured = new ConcurrentHashMap<>();

    public void setFeatured(Long userId, String title, String summary, String dDayText, List<String> tags) {
        userFeatured.put(userId, new FeaturedEventState(title, summary, dDayText, tags));
    }

    public FeaturedEventState getFeatured(Long userId) {
        return userFeatured.get(userId);
    }

    public record FeaturedEventState(String title, String summary, String dDayText, List<String> tags) {}
}
