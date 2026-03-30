package com.project.server.service.auth;

import com.project.server.dto.WatchAssetDto;
import com.project.server.dto.HomeDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class WatchAssetSelectionService {

    private static final List<WatchAssetDto.Asset> ALL_ASSETS = List.of(
            WatchAssetDto.Asset.builder().assetName("장기채 ETF").changePercent(-1.2).build(),
            WatchAssetDto.Asset.builder().assetName("나스닥 성장주 ETF").changePercent(-0.8).build(),
            WatchAssetDto.Asset.builder().assetName("달러 인덱스 ETF").changePercent(0.6).build(),
            WatchAssetDto.Asset.builder().assetName("금 ETF").changePercent(1.5).build(),
            WatchAssetDto.Asset.builder().assetName("비트코인 ETF").changePercent(3.2).build(),
            WatchAssetDto.Asset.builder().assetName("코스피 ETF").changePercent(-0.5).build()
    );

    private final Map<Long, List<WatchAssetDto.Asset>> userAssets = new ConcurrentHashMap<>();

    public List<WatchAssetDto.Asset> getAllAssets() {
        return ALL_ASSETS;
    }

    public List<WatchAssetDto.Asset> getSelectedAssets(Long userId) {
        return userAssets.computeIfAbsent(userId, id -> new ArrayList<>(ALL_ASSETS.subList(0, 3)));
    }

    public List<WatchAssetDto.Asset> updateSelectedAssets(Long userId, List<String> assetNames) {
        List<String> safeNames = assetNames == null ? List.of() : assetNames;
        List<WatchAssetDto.Asset> selected = ALL_ASSETS.stream()
                .filter(asset -> safeNames.contains(asset.getAssetName()))
                .limit(3)
                .collect(Collectors.toCollection(ArrayList::new));

        if (selected.isEmpty()) {
            selected = new ArrayList<>(ALL_ASSETS.subList(0, 3));
        }

        userAssets.put(userId, selected);
        return selected;
    }

    public List<HomeDto.WatchAssetImpact> getWatchImpacts(Long userId) {
        return getSelectedAssets(userId).stream()
                .map(asset -> HomeDto.WatchAssetImpact.builder()
                        .assetName(asset.getAssetName())
                        .signalText(asset.getChangePercent() >= 0 ? "상승확률 68%" : "하락확률 62%")
                        .build())
                .toList();
    }
}
