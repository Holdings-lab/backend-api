package com.project.server.service.asset.session;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class InMemoryUserSessionStore implements UserSessionStore {

    private final Map<Long, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final Map<Long, Instant> refreshLocks = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastSyncAt = new ConcurrentHashMap<>();
    private final Map<Long, Instant> nextBackgroundSyncAt = new ConcurrentHashMap<>();

    @Override
    public void activateSession(Long userId, String deviceId, Duration ttl) {
        sessions.put(userId, new SessionEntry(deviceId, Instant.now().plus(ttl)));
    }

    @Override
    public boolean isSessionActive(Long userId) {
        SessionEntry entry = sessions.get(userId);
        if (entry == null) {
            return false;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            sessions.remove(userId);
            nextBackgroundSyncAt.remove(userId);
            return false;
        }
        return true;
    }

    @Override
    public void terminateSession(Long userId) {
        sessions.remove(userId);
        refreshLocks.remove(userId);
        nextBackgroundSyncAt.remove(userId);
    }

    @Override
    public Set<Long> getActiveUserIds() {
        return sessions.keySet().stream()
                .filter(this::isSessionActive)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isRefreshLocked(Long userId) {
        Instant lockedUntil = refreshLocks.get(userId);
        if (lockedUntil == null) {
            return false;
        }
        if (Instant.now().isAfter(lockedUntil)) {
            refreshLocks.remove(userId);
            return false;
        }
        return true;
    }

    @Override
    public void setRefreshLock(Long userId, Duration duration) {
        refreshLocks.put(userId, Instant.now().plus(duration));
    }

    @Override
    public boolean isThrottleOpen(Long userId, Duration minInterval) {
        Instant last = lastSyncAt.get(userId);
        if (last == null) {
            return true;
        }
        return Instant.now().isAfter(last.plus(minInterval));
    }

    @Override
    public void markSynced(Long userId) {
        lastSyncAt.put(userId, Instant.now());
    }

    @Override
    public Optional<Instant> getNextBackgroundSyncAt(Long userId) {
        return Optional.ofNullable(nextBackgroundSyncAt.get(userId));
    }

    @Override
    public void scheduleNextBackgroundSync(Long userId, Duration delay) {
        nextBackgroundSyncAt.put(userId, Instant.now().plus(delay));
    }

    private record SessionEntry(String deviceId, Instant expiresAt) {
    }
}
