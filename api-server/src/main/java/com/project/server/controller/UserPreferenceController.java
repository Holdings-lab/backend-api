package com.project.server.controller;

import com.project.server.dto.UserPreferenceDto;
import com.project.server.dto.AuthDto;
import com.project.server.dto.ActionDto;
import com.project.server.dto.WatchAssetDto;
import com.project.server.service.auth.WatchAssetSelectionService;
import com.project.server.service.auth.UserPreferenceService;
import com.project.server.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;
    private final AuthService authService;
    private final WatchAssetSelectionService watchAssetSelectionService;

    @GetMapping
    public ResponseEntity<AuthDto.MeResponse> getMe(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(authService.getMe(userId));
    }

    @GetMapping("/settings/notifications")
    public ResponseEntity<UserPreferenceDto.NotificationSettingsResponse> getNotificationSettings(
            @RequestParam(name = "userId", defaultValue = "1") Long userId
    ) {
        return ResponseEntity.ok(userPreferenceService.getNotificationSettings(userId));
    }

    @PatchMapping("/settings/notifications")
    public ResponseEntity<UserPreferenceDto.NotificationSettingsResponse> updateNotificationSettings(
            @RequestParam(name = "userId", defaultValue = "1") Long userId,
            @RequestBody UserPreferenceDto.UpdateNotificationSettingsRequest request
    ) {
        return ResponseEntity.ok(userPreferenceService.updateNotificationSettings(userId, request));
    }

    @GetMapping("/watch-assets/options")
    public ResponseEntity<WatchAssetDto.AssetListResponse> getWatchAssetOptions() {
        return ResponseEntity.ok(
                WatchAssetDto.AssetListResponse.builder()
                        .assets(watchAssetSelectionService.getAllAssets())
                        .build()
        );
    }

    @PostMapping("/watch-assets")
    public ResponseEntity<ActionDto.ActionResponse> updateWatchAssets(
            @RequestParam(name = "userId", defaultValue = "1") Long userId,
            @RequestBody WatchAssetDto.UpdateWatchAssetsRequest request
    ) {
        watchAssetSelectionService.updateSelectedAssets(userId, request.getAssetNames());
        return ResponseEntity.ok(ActionDto.ActionResponse.builder().action("watch-assets-update").status("completed").build());
    }
}
