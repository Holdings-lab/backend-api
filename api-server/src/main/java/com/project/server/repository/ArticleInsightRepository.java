package com.project.server.repository;

import com.project.server.domain.ArticleInsightEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ArticleInsightRepository extends JpaRepository<ArticleInsightEntity, Long> {
    Optional<ArticleInsightEntity> findTopByDocumentId(Long documentId);

    List<ArticleInsightEntity> findByInsightDateOrderByIdDesc(LocalDate insightDate);
}
