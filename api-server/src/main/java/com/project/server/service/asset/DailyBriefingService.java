package com.project.server.service.asset;

import com.project.server.dto.UserAssetDto;
import com.project.server.service.asset.sync.SyncOrchestratorService;
import com.project.server.service.asset.sync.SyncTrigger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DailyBriefingService {

    private final SyncOrchestratorService syncOrchestratorService;
    private final AssetMetricsService assetMetricsService;
    private final BriefingMessageComposer briefingMessageComposer;

    public UserAssetDto.DailyBriefingResponse getDailyBriefing(Long userId, boolean refresh) {
        if (refresh) {
            syncOrchestratorService.syncIfNeeded(userId, SyncTrigger.PULL_REFRESH);
        }

        AssetMetricsService.AssetMetrics metrics = assetMetricsService.compute(userId);
        String message = briefingMessageComposer.compose(
                metrics.status(), metrics.drawdownPct(), metrics.investmentHorizon());

        return UserAssetDto.DailyBriefingResponse.builder()
                .assetTotal(metrics.assetTotal())
                .dailyChangePct(metrics.dailyChangePct())
                .drawdownPct(metrics.drawdownPct())
                .maxDrawdownTolerance(metrics.maxDrawdownTolerance())
                .ratio(metrics.ratio())
                .status(metrics.status().name())
                .message(message)
                .build();
    }
}
