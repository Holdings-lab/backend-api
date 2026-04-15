package com.project.server.service.auth;

import com.project.server.domain.UserWatchAssetEntity;
import com.project.server.domain.WatchAssetCatalogEntity;
import com.project.server.dto.WatchAssetDto;
import com.project.server.dto.HomeDto;
import com.project.server.repository.UserWatchAssetRepository;
import com.project.server.repository.WatchAssetCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WatchAssetSelectionService {

    private static final int DEFAULT_SELECTION_SIZE = 3;

    private final WatchAssetCatalogRepository watchAssetCatalogRepository;
    private final UserWatchAssetRepository userWatchAssetRepository;

    @Transactional(readOnly = true)
    public List<WatchAssetDto.Asset> getAllAssets() {
        return watchAssetCatalogRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(asset -> WatchAssetDto.Asset.builder()
                        .assetName(asset.getAssetName())
                        .changePercent(asset.getDefaultChangePercent())
                        .build())
                .toList();
    }

    @Transactional
    public List<WatchAssetDto.Asset> getSelectedAssets(Long userId) {
        List<UserWatchAssetEntity> selected = userWatchAssetRepository.findByUserIdOrderByDisplayOrderAsc(userId);
        if (!selected.isEmpty()) {
            return selected.stream()
                    .map(this::toAsset)
                    .toList();
        }

        List<WatchAssetCatalogEntity> defaults = watchAssetCatalogRepository.findAllByOrderByDisplayOrderAsc()
                .stream()
                .limit(DEFAULT_SELECTION_SIZE)
                .toList();
        if (defaults.isEmpty()) {
            return List.of();
        }

        List<UserWatchAssetEntity> seeds = new ArrayList<>();
        int displayOrder = 1;
        for (WatchAssetCatalogEntity asset : defaults) {
            seeds.add(UserWatchAssetEntity.builder()
                    .userId(userId)
                    .assetName(asset.getAssetName())
                    .changePercent(asset.getDefaultChangePercent())
                    .signalText(toSignalText(asset.getDefaultChangePercent()))
                    .displayOrder(displayOrder++)
                    .build());
        }
        userWatchAssetRepository.saveAll(seeds);
        return seeds.stream().map(this::toAsset).toList();
    }

    @Transactional
    public List<WatchAssetDto.Asset> updateSelectedAssets(Long userId, List<String> assetNames) {
        List<String> safeNames = assetNames == null ? List.of() : assetNames;
        List<WatchAssetCatalogEntity> selectedCatalog = watchAssetCatalogRepository.findByAssetNameIn(safeNames)
                .stream()
                .sorted(Comparator.comparingInt(a -> safeNames.indexOf(a.getAssetName())))
                .limit(DEFAULT_SELECTION_SIZE)
                .toList();

        if (selectedCatalog.isEmpty()) {
            selectedCatalog = watchAssetCatalogRepository.findAllByOrderByDisplayOrderAsc()
                    .stream()
                    .limit(DEFAULT_SELECTION_SIZE)
                    .toList();
        }

        userWatchAssetRepository.deleteByUserId(userId);
        List<UserWatchAssetEntity> saved = new ArrayList<>();
        int displayOrder = 1;
        for (WatchAssetCatalogEntity catalog : selectedCatalog) {
            UserWatchAssetEntity entity = UserWatchAssetEntity.builder()
                    .userId(userId)
                    .assetName(catalog.getAssetName())
                    .changePercent(catalog.getDefaultChangePercent())
                    .signalText(toSignalText(catalog.getDefaultChangePercent()))
                    .displayOrder(displayOrder++)
                    .build();
            saved.add(entity);
        }

        return userWatchAssetRepository.saveAll(saved).stream().map(this::toAsset).collect(Collectors.toList());
    }

    @Transactional
    public List<HomeDto.WatchAssetImpact> getWatchImpacts(Long userId) {
        return getSelectedAssets(userId).stream()
                .map(asset -> HomeDto.WatchAssetImpact.builder()
                        .assetName(asset.getAssetName())
                        .signalText(toSignalText(asset.getChangePercent()))
                        .build())
                .toList();
    }

    private WatchAssetDto.Asset toAsset(UserWatchAssetEntity entity) {
        return WatchAssetDto.Asset.builder()
                .assetName(entity.getAssetName())
                .changePercent(entity.getChangePercent())
                .build();
    }

    private String toSignalText(Double changePercent) {
        double value = changePercent == null ? 0.0 : changePercent;
        if (value >= 0.7) {
            return "상승확률 72%";
        }
        if (value >= 0) {
            return "상승확률 60%";
        }
        if (value <= -0.7) {
            return "하락확률 70%";
        }
        return "하락확률 58%";
    }
}
