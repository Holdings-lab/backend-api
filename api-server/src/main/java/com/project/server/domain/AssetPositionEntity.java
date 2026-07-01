package com.project.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "asset_positions", indexes = {
    @Index(name = "idx_user_account", columnList = "user_id,account_id"),
    @Index(name = "idx_account_symbol", columnList = "account_id,symbol")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    @Column(name = "item_code", length = 20)
    private String itemCode;

    @Column(name = "item_name", length = 100)
    private String itemName;

    @Column(name = "product_code", length = 10)
    private String productCode;

    @Column(name = "overseas_yn", length = 1)
    private String overseasYn;

    @Column(name = "position_type", length = 20)
    private String positionType;

    @Column(name = "quantity", precision = 15, scale = 2)
    private BigDecimal quantity;

    @Column(name = "purchase_price", precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "current_price", precision = 15, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "current_value", precision = 18, scale = 2)
    private BigDecimal currentValue;

    @Column(name = "purchase_amount", precision = 18, scale = 2)
    private BigDecimal purchaseAmount;

    @Column(name = "gain_loss", precision = 18, scale = 2)
    private BigDecimal gainLoss;

    @Column(name = "gain_loss_rate", precision = 10, scale = 4)
    private BigDecimal gainLossRate;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;  // KRW, USD

    @Column(name = "purchased_at")
    private LocalDate purchasedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
