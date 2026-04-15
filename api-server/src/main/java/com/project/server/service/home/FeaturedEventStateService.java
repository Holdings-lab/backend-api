package com.project.server.service.home;

import com.project.server.domain.FeaturedEventStateEntity;
import com.project.server.repository.FeaturedEventStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeaturedEventStateService {

    private final FeaturedEventStateRepository featuredEventStateRepository;

    @Transactional
    public void setFeatured(Long userId, String title, String summary, String dDayText, List<String> tags) {
        FeaturedEventStateEntity entity = featuredEventStateRepository.findByUserId(userId)
                .orElseGet(() -> FeaturedEventStateEntity.builder().userId(userId).build());

        entity.setTitle(title);
        entity.setSummary(summary);
        entity.setDDayText(dDayText);
        entity.setTagsCsv(String.join(",", tags == null ? List.of() : tags));
        entity.setUpdatedAt(LocalDateTime.now());
        featuredEventStateRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public FeaturedEventState getFeatured(Long userId) {
        return featuredEventStateRepository.findByUserId(userId)
                .map(entity -> new FeaturedEventState(
                        entity.getTitle(),
                        entity.getSummary(),
                        entity.getDDayText(),
                        entity.getTagsCsv() == null || entity.getTagsCsv().isBlank()
                                ? List.of()
                                : List.of(entity.getTagsCsv().split(","))
                ))
                .orElse(null);
    }

    public record FeaturedEventState(String title, String summary, String dDayText, List<String> tags) {}
}
