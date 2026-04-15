package com.project.server.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "featured_event_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedEventStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "summary", nullable = false, length = 1000)
    private String summary;

    @Column(name = "d_day_text", nullable = false, length = 40)
    private String dDayText;

    @Column(name = "tags_csv", nullable = false, length = 500)
    private String tagsCsv;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
