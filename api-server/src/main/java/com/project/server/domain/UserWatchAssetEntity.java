package com.project.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_watch_assets")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWatchAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "asset_name", nullable = false, length = 120)
    private String assetName;

    @Column(name = "change_percent", nullable = false)
    private Double changePercent;

    @Column(name = "signal_text", nullable = false, length = 120)
    private String signalText;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
