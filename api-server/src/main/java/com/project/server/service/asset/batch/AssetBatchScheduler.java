package com.project.server.service.asset.batch;

import com.project.server.config.AssetProperties;
import com.project.server.service.asset.AssetSnapshotService;
import com.project.server.service.asset.session.UserSessionStore;
import com.project.server.service.asset.sync.SyncOrchestratorService;
import com.project.server.service.asset.sync.SyncTrigger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class AssetBatchScheduler {

    private final AssetSnapshotService assetSnapshotService;
    private final UserSessionStore sessionStore;
    private final SyncOrchestratorService syncOrchestratorService;
    private final AssetProperties assetProperties;

    @Scheduled(cron = "${asset.batch.previous-day-snapshot-cron:0 30 8 * * *}")
    public void capturePreviousDaySnapshots() {
        log.info("Running previous-day asset snapshot job");
        assetSnapshotService.capturePreviousDaySnapshots();
    }

    @Scheduled(cron = "${asset.batch.all-time-high-scan-cron:0 30 6 * * 2-6}")
    public void scanAllTimeHighs() {
        log.info("Running ATH scan job (US market close basis)");
        assetSnapshotService.scanAllTimeHighs();
    }

    @Scheduled(fixedDelayString = "${asset.session.background-sync-interval-ms:60000}")
    public void backgroundSync() {
        Set<Long> activeUserIds = sessionStore.getActiveUserIds();
        Duration throttle = Duration.ofMinutes(assetProperties.getSession().getSyncThrottleMinutes());

        for (Long userId : activeUserIds) {
            Instant nextAt = sessionStore.getNextBackgroundSyncAt(userId).orElse(Instant.EPOCH);
            if (Instant.now().isBefore(nextAt)) {
                continue;
            }

            var result = syncOrchestratorService.syncIfNeeded(userId, SyncTrigger.BACKGROUND);
            if (result.skipped()) {
                sessionStore.scheduleNextBackgroundSync(userId, throttle);
            } else if (result.success()) {
                sessionStore.scheduleNextBackgroundSync(userId, throttle);
            }
        }
    }
}
