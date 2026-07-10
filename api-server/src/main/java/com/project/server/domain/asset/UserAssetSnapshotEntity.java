package com.project.server.domain.asset;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_asset_snapshots", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_asset_snapshots", columnNames = {"user_id", "snapshot_type", "snapshot_date"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAssetSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_type", nullable = false, length = 30)
    private AssetSnapshotType snapshotType;

    @Column(name = "asset_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal assetTotal;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "snapshot_at", nullable = false)
    private LocalDateTime snapshotAt;

    @PrePersist
    void onCreate() {
        if (snapshotAt == null) {
            snapshotAt = LocalDateTime.now();
        }
    }
}
