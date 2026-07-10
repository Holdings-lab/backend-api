package com.project.server.service.asset.session;

import com.project.server.config.AssetProperties;
import com.project.server.domain.asset.UserSyncStateEntity;
import com.project.server.exception.ApiException;
import com.project.server.repository.asset.UserSyncStateRepository;
import com.project.server.service.asset.sync.SyncOrchestratorService;
import com.project.server.service.asset.sync.SyncTrigger;
import com.project.server.service.asset.sync.UserSyncResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionStore sessionStore;
    private final UserSyncStateRepository syncStateRepository;
    private final SyncOrchestratorService syncOrchestratorService;
    private final AssetProperties assetProperties;

    @Transactional
    public UserSyncResult heartbeat(Long userId, String deviceId, boolean appOpen) {
        validateUserId(userId);

        Duration ttl = Duration.ofMinutes(assetProperties.getSession().getHeartbeatTtlMinutes());
        sessionStore.activateSession(userId, deviceId, ttl);

        UserSyncStateEntity state = syncStateRepository.findById(userId)
                .orElse(UserSyncStateEntity.builder().userId(userId).build());
        state.setLastClientHeartbeatAt(LocalDateTime.now());
        state.setSessionStatus(UserSyncStateEntity.SessionStatus.ACTIVE);
        syncStateRepository.save(state);

        if (appOpen) {
            return syncOrchestratorService.syncIfNeeded(userId, SyncTrigger.APP_OPEN);
        }
        return UserSyncResult.skipped("HEARTBEAT_ONLY");
    }

    @Transactional
    public void terminate(Long userId) {
        validateUserId(userId);
        sessionStore.terminateSession(userId);

        syncStateRepository.findById(userId).ifPresent(state -> {
            state.setSessionStatus(UserSyncStateEntity.SessionStatus.EXPIRED);
            syncStateRepository.save(state);
        });
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
    }
}
