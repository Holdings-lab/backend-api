package com.project.server.domain.asset;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

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
    @Column(name = "investment_horizon", length = 30)
    private InvestmentHorizon investmentHorizon;

    @Column(name = "max_drawdown_tolerance")
    private Integer maxDrawdownTolerance;

    @Enumerated(EnumType.STRING)
    @Column(name = "investment_style", length = 30)
    private InvestmentStyle investmentStyle;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_interest_sectors", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "sector", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<InterestSector> interests = new LinkedHashSet<>();

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
        if (interests == null) {
            interests = new LinkedHashSet<>();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
