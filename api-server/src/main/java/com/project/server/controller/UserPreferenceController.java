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

    @GetMapping("/{userId}")
    public ResponseEntity<AuthDto.MeResponse> getMeByPath(
            @PathVariable("userId") Long userId
    ) {
        return ResponseEntity.ok(authService.getMe(userId));
    }

    @GetMapping("/{userId}/profile")
        public ResponseEntity<AuthDto.Profile> getMeProfile(
            @PathVariable("userId") Long userId
        ) {
        return ResponseEntity.ok(authService.getMeProfile(userId));
    }

    @GetMapping("/{userId}/watch-assets")
        public ResponseEntity<java.util.List<AuthDto.WatchAssetReturn>> getMeWatchAssets(
            @PathVariable("userId") Long userId
        ) {
        return ResponseEntity.ok(authService.getMeWatchAssets(userId));
    }

    @GetMapping("/{userId}/study-stats")
        public ResponseEntity<java.util.List<AuthDto.StudyStat>> getMeStudyStats(
            @PathVariable("userId"  ) Long userId
        ) {
        return ResponseEntity.ok(authService.getMeStudyStats(userId));
    }

    @GetMapping("/{userId}/settings")
    public ResponseEntity<java.util.List<AuthDto.SettingMenuItem>> getSettings(
            @PathVariable("userId") Long userId
    ) {
        return ResponseEntity.ok(authService.getMeSettings(userId));
    }

    @PatchMapping("/{userId}/settings")
    public ResponseEntity<java.util.List<AuthDto.SettingMenuItem>> updateSettings(
            @PathVariable("userId") Long userId,
            @RequestBody UserPreferenceDto.UpdateSettingsRequest request
    ) {
        return ResponseEntity.ok(userPreferenceService.updateSettings(userId, request));
    }

    @GetMapping("/watch-assets/options")
    public ResponseEntity<WatchAssetDto.AssetListResponse> getWatchAssetOptions() {
        return ResponseEntity.ok(
                WatchAssetDto.AssetListResponse.builder()
                        .assets(watchAssetSelectionService.getAllAssets())
                        .build()
        );
    }

    @PostMapping("/{userId}/watch-assets")
    public ResponseEntity<ActionDto.ActionResponse> updateWatchAssets(
            @PathVariable("userId") Long userId,
            @RequestBody WatchAssetDto.UpdateWatchAssetsRequest request
    ) {
        watchAssetSelectionService.updateSelectedAssets(userId, request.getAssetNames());
        return ResponseEntity.ok(ActionDto.ActionResponse.builder().action("watch-assets-update").status("completed").build());
    }
}
