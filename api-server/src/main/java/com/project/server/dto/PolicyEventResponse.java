package com.project.server.dto;

import com.project.server.domain.PolicyEvent;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PolicyEventResponse {
    private Long id;
    private String title;
    private String keyword;
    private Double impactScore;
    private String analysisSummary;
    private LocalDateTime createdAt;
    
    public static PolicyEventResponse from(PolicyEvent event) {
        return PolicyEventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .keyword(event.getKeyword())
                .impactScore(event.getImpactScore())
                .analysisSummary(event.getAnalysisSummary())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
