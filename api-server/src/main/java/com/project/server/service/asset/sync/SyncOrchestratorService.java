package com.project.server.service.asset.sync;

import com.project.server.config.AssetProperties;
import com.project.server.domain.asset.UserSyncStateEntity;
import com.project.server.repository.asset.UserSyncStateRepository;
import com.project.server.service.asset.session.UserSessionStore;
import com.project.server.service.broker.AssetSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class SyncOrchestratorService {

    private final UserSessionStore sessionStore;
    private final AssetSyncService assetSyncService;
    private final UserSyncStateRepository syncStateRepository;
    private final AssetProperties assetProperties;

    @Transactional
    public UserSyncResult syncIfNeeded(Long userId, SyncTrigger trigger) {
        return switch (trigger) {
            case PULL_REFRESH -> handlePullRefresh(userId);
            case APP_OPEN -> handleForcedSync(userId);
            case BACKGROUND -> handleBackgroundSync(userId);
        };
    }

    private UserSyncResult handlePullRefresh(Long userId) {
        if (sessionStore.isRefreshLocked(userId)) {
            return UserSyncResult.skipped("REFRESH_LOCKED");
        }

        sessionStore.setRefreshLock(userId, Duration.ofSeconds(assetProperties.getSession().getRefreshLockSeconds()));
        return executeSync(userId);
    }

    private UserSyncResult handleForcedSync(Long userId) {
        if (sessionStore.isRefreshLocked(userId)) {
            return UserSyncResult.skipped("REFRESH_LOCKED");
        }
        return executeSync(userId);
    }

    private UserSyncResult handleBackgroundSync(Long userId) {
        if (!sessionStore.isSessionActive(userId)) {
            return UserSyncResult.skipped("SESSION_INACTIVE");
        }
        if (sessionStore.isRefreshLocked(userId)) {
            return UserSyncResult.skipped("REFRESH_LOCKED");
        }
        if (!sessionStore.isThrottleOpen(userId, Duration.ofMinutes(assetProperties.getSession().getSyncThrottleMinutes()))) {
            return UserSyncResult.skipped("THROTTLED");
        }
        return executeSync(userId);
    }

    private UserSyncResult executeSync(Long userId) {
        try {
            assetSyncService.syncAllConnectedAccounts(userId);
            sessionStore.markSynced(userId);
            updateSyncState(userId);
            return UserSyncResult.succeeded();
        } catch (Exception e) {
            log.warn("User sync failed for userId={}: {}", userId, e.getMessage());
            return UserSyncResult.failed(e.getMessage());
        }
    }

    private void updateSyncState(Long userId) {
        UserSyncStateEntity state = syncStateRepository.findById(userId)
                .orElse(UserSyncStateEntity.builder().userId(userId).build());
        state.setLastHyphenSyncAt(LocalDateTime.now());
        state.setSessionStatus(sessionStore.isSessionActive(userId)
                ? UserSyncStateEntity.SessionStatus.ACTIVE
                : UserSyncStateEntity.SessionStatus.EXPIRED);
        syncStateRepository.save(state);
    }
}
