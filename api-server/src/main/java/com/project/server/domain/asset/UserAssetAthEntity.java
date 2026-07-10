package com.project.server.domain.asset;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_asset_ath")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAssetAthEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "all_time_high_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal allTimeHighAmount;

    @Column(name = "all_time_high_date", nullable = false)
    private LocalDate allTimeHighDate;

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
