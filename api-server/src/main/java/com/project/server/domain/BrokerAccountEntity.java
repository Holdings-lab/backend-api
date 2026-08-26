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

    /** 앱 사용자 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "broker_name", nullable = false, length = 50)
    private String brokerName;

    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;

    @Column(name = "account_nickname", length = 100)
    private String accountNickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status")
    private ConnectionStatus connectionStatus;

    /** AES 암호화된 한투 appkey. ENV(KIS_MOCK_*) 사용 시 null */
    @Column(name = "app_key", length = 500)
    private String appKey;

    /** AES 암호화된 한투 appsecret. ENV 사용 시 null */
    @Column(name = "app_secret", length = 500)
    private String appSecret;

    /** 한투 계좌상품코드 ACNT_PRDT_CD */
    @Column(name = "account_product_code", length = 10)
    private String accountProductCode;

    @Column(name = "account_owner_name", length = 100)
    private String accountOwnerName;

    @Column(name = "account_type", length = 20)
    private String accountType;

    /** 계좌 스냅샷 JSON */
    @Column(name = "account_details", columnDefinition = "TEXT")
    private String accountDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "credential_source", length = 20)
    private CredentialSource credentialSource;

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
        if (connectionStatus == null) {
            connectionStatus = ConnectionStatus.PENDING;
        }
        if (credentialSource == null) {
            credentialSource = CredentialSource.ENV;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ConnectionStatus {
        CONNECTED,
        PENDING,
        EXPIRED,
        DISCONNECTED,
        ERROR
    }

    public enum CredentialSource {
        ENV,
        USER
    }
}
