package com.project.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "policy_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "keyword", nullable = false, length = 120)
    private String keyword;

    @Column(name = "impact_score", nullable = false)
    private Double impactScore;

    @Column(name = "analysis_summary", nullable = false, length = 1000)
    private String analysisSummary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
