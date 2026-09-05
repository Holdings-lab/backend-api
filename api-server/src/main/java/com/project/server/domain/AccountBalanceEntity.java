package com.project.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "account_balances", indexes = {
    @Index(name = "idx_user_account_date", columnList = "user_id,account_id,as_of_date")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountBalanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "total_asset_value", precision = 18, scale = 2)
    private BigDecimal totalAssetValue;

    @Column(name = "cash_balance", precision = 18, scale = 2)
    private BigDecimal cashBalance;

    @Column(name = "deposit_amount", precision = 18, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "evaluation_amount", precision = 18, scale = 2)
    private BigDecimal evaluationAmount;

    @Column(name = "gain_loss", precision = 18, scale = 2)
    private BigDecimal gainLoss;

    @Column(name = "gain_loss_rate", precision = 10, scale = 4)
    private BigDecimal gainLossRate;

    @Column(name = "daily_gain_loss", precision = 18, scale = 2)
    private BigDecimal dailyGainLoss;

    @Column(name = "daily_gain_loss_rate", precision = 10, scale = 4)
    private BigDecimal dailyGainLossRate;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fx_rates", columnDefinition = "jsonb")
    private Map<String, BigDecimal> fxRates;

    @Column(name = "as_of_date")
    private LocalDate asOfDate;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (asOfDate == null) {
            asOfDate = LocalDate.now();
        }
    }
}
