package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

public class WatchAssetDto {

    @Data
    @Builder
    public static class Asset {
        private String assetName;
        private double changePercent;
    }

    @Data
    @Builder
    public static class AssetListResponse {
        private List<Asset> assets;
    }

    @Data
    public static class UpdateWatchAssetsRequest {
        private List<String> assetNames;
    }
}
