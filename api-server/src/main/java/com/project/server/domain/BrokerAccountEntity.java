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
    @Column(name = "hyphen_status")
    private HyphenStatus hyphenStatus;
    
    /** AES 암호화된 하이픈 증권사 로그인 사용자 ID */
    @Column(name = "hyphen_user_id", length = 255)
    private String hyphenUserId;
    
    /** AES 암호화된 하이픈 증권사 로그인 비밀번호 */
    @Column(name = "hyphen_user_password", length = 500)
    private String hyphenUserPassword;

    /** AES 암호화된 증권사 계좌 비밀번호 (일부 증권사 조회 시 필요) */
    @Column(name = "hyphen_account_password", length = 255)
    private String hyphenAccountPassword;

    @Column(name = "account_owner_name", length = 100)
    private String accountOwnerName;

    @Column(name = "account_type", length = 20)
    private String accountType;

    /** 하이픈 전계좌조회(0534) 계좌 1건 JSON */
    @Column(name = "hyphen_account_details", columnDefinition = "TEXT")
    private String hyphenAccountDetails;

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
        if (hyphenStatus == null) {
            hyphenStatus = HyphenStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum HyphenStatus {
        CONNECTED,      // 정상 연동됨
        PENDING,        // 연동 대기 중
        EXPIRED,        // 토큰 만료
        DISCONNECTED,   // 연동 해제
        ERROR           // 에러 발생
    }
}
