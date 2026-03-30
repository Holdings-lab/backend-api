package com.project.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_profiles_user_id", columnNames = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "avatar_text", nullable = false, length = 10)
    private String avatarText;

    @Column(name = "weekly_learning_count", nullable = false)
    private Integer weeklyLearningCount;

    @Column(name = "quiz_accuracy_percent", nullable = false)
    private Integer quizAccuracyPercent;

    @Column(name = "weak_topic", nullable = false, length = 120)
    private String weakTopic;
}
