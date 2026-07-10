package com.project.server.domain.asset;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_sync_state")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSyncStateEntity {

    public enum SessionStatus {
        ACTIVE,
        EXPIRED
    }

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "last_hyphen_sync_at")
    private LocalDateTime lastHyphenSyncAt;

    @Column(name = "last_client_heartbeat_at")
    private LocalDateTime lastClientHeartbeatAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 20)
    private SessionStatus sessionStatus;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (sessionStatus == null) {
            sessionStatus = SessionStatus.EXPIRED;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
