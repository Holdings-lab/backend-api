package com.project.server.domain.asset;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_investment_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInvestmentProfileEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_horizon", nullable = false, length = 30)
    private InvestmentHorizon investmentHorizon;

    @Column(name = "max_drawdown_tolerance", nullable = false)
    private Integer maxDrawdownTolerance;

    @Column(name = "onboarded_at", nullable = false)
    private LocalDateTime onboardedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (onboardedAt == null) {
            onboardedAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (investmentHorizon == null) {
            investmentHorizon = InvestmentHorizon.ONE_TO_THREE_YEARS;
        }
        if (maxDrawdownTolerance == null) {
            maxDrawdownTolerance = 10;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
