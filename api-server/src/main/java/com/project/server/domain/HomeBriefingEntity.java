package com.project.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "home_briefings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeBriefingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "briefing_date", nullable = false)
    private LocalDate briefingDate;

    @Column(name = "briefing_headline", nullable = false, length = 500)
    private String briefingHeadline;

    @Column(name = "briefing_paragraphs", nullable = false, columnDefinition = "jsonb")
    private String briefingParagraphsJson;

    @Column(name = "push_data", nullable = false, columnDefinition = "jsonb")
    private String pushDataJson;

    @Column(name = "llm_provider", nullable = false, length = 40)
    private String llmProvider;

    @Column(name = "llm_model", nullable = false, length = 120)
    private String llmModel;

    @Column(name = "prompt_version", nullable = false, length = 80)
    private String promptVersion;

    @Column(name = "briefing_payload", nullable = false, columnDefinition = "jsonb")
    private String briefingPayloadJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
