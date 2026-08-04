package com.project.server.domain.asset;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_onboarding_progress")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOnboardingProgressEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "account_skipped", nullable = false)
    @Builder.Default
    private boolean accountSkipped = false;

    /** goal 테이블 저장 전, 1단계에서만 쓰는 임시 투자 목적 */
    @Enumerated(EnumType.STRING)
    @Column(name = "draft_financial_goal", length = 30)
    private FinancialGoal draftFinancialGoal;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
