package com.project.server.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEvent {
    private Long id;
    private String title;
    private String keyword;
    private Double impactScore;
    private String analysisSummary;
    private LocalDateTime createdAt;
}
