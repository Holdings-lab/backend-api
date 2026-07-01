package com.project.server.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "hyphen_sync_history", indexes = {
    @Index(name = "idx_hyphen_sync_history_user_account_status", columnList = "user_id,account_id,status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyphenSyncHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "sync_type", nullable = false, length = 30)
    private String syncType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SyncStatus status;

    @Column(name = "error_code", length = 20)
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "sync_duration_ms")
    private Integer syncDurationMs;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    public enum SyncStatus {
        PENDING,
        IN_PROGRESS,
        SUCCESS,
        FAILURE
    }
}
