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
@Table(name = "article_insights")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleInsightEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false, unique = true)
    private Long documentId;

    @Column(name = "insight_date", nullable = false)
    private LocalDate insightDate;

    @Column(name = "summary", nullable = false, columnDefinition = "text")
    private String summary;

    @Column(name = "keywords", nullable = false, columnDefinition = "jsonb")
    private String keywordsJson;

    @Column(name = "asset_impacts", nullable = false, columnDefinition = "jsonb")
    private String assetImpactsJson;

    @Column(name = "llm_provider", nullable = false, length = 40)
    private String llmProvider;

    @Column(name = "llm_model", nullable = false, length = 120)
    private String llmModel;

    @Column(name = "prompt_version", nullable = false, length = 80)
    private String promptVersion;

    @Column(name = "insight_payload", nullable = false, columnDefinition = "jsonb")
    private String insightPayloadJson;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
