package com.project.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "broker_accounts", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_broker_account", columnNames = {"user_id", "broker_name", "account_number"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "broker_name", nullable = false, length = 50)
    private String brokerName;  // 'KIS' (한국투자증권)

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_nickname", length = 100)
    private String accountNickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "codef_status")
    private CodefStatus codefStatus;

    @Column(name = "codef_token_id", length = 255)
    private String codefTokenId;

    @Column(name = "codef_token_secret", length = 500)
    private String codefTokenSecret;  // AES 암호화

    @Column(name = "connected_id", length = 255)
    private String connectedId; // CODEF connectedId (스크래핑 식별자)

    @Column(name = "account_owner_name", length = 100)
    private String accountOwnerName;

    @Column(name = "account_type", length = 20)
    private String accountType;  // STOCK, FUTURES, OPTION, FUND

    @Column(name = "codef_account_details", columnDefinition = "TEXT")
    private String codefAccountDetails;  // CODEF 응답 전체 JSON 저장

    @Column(name = "is_primary")
    private Boolean isPrimary;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "sync_count")
    private Integer syncCount;

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
        if (syncCount == null) {
            syncCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum CodefStatus {
        CONNECTED,      // 정상 연동됨
        PENDING,        // 연동 대기 중
        EXPIRED,        // 토큰 만료
        DISCONNECTED,   // 연동 해제
        ERROR           // 에러 발생
    }
}
