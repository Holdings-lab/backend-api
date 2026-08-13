package com.project.server.controller;

import com.project.server.dto.WatchAssetDto;
import com.project.server.service.auth.WatchAssetSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관심자산 카탈로그(옵션 목록). 선택 저장은 /api/me/watch-assets.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/watch-assets")
@RequiredArgsConstructor
public class WatchAssetController {

    private final WatchAssetSelectionService watchAssetSelectionService;

    @GetMapping("/options")
    public ResponseEntity<WatchAssetDto.AssetListResponse> getWatchAssetOptions() {
        return ResponseEntity.ok(
                WatchAssetDto.AssetListResponse.builder()
                        .assets(watchAssetSelectionService.getAllAssets())
                        .build()
        );
    }
}
