package com.project.server.service.asset.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface UserSessionStore {

    void activateSession(Long userId, String deviceId, Duration ttl);

    boolean isSessionActive(Long userId);

    void terminateSession(Long userId);

    Set<Long> getActiveUserIds();

    boolean isRefreshLocked(Long userId);

    void setRefreshLock(Long userId, Duration duration);

    boolean isThrottleOpen(Long userId, Duration minInterval);

    void markSynced(Long userId);

    Optional<Instant> getNextBackgroundSyncAt(Long userId);

    void scheduleNextBackgroundSync(Long userId, Duration delay);
}
