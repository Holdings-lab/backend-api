package com.project.server.domain.asset;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_investment_goals")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInvestmentGoalEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "financial_goal", nullable = false, length = 30)
    private FinancialGoal financialGoal;

    @Column(name = "goal_label", nullable = false, length = 100)
    private String goalLabel;

    @Column(name = "target_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal targetAmount;

    @Column(name = "goal_start_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal goalStartAmount;

    @Column(name = "goal_start_date", nullable = false)
    private LocalDate goalStartDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
