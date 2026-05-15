package com.project.server.controller;

import com.project.server.dto.UserPreferenceDto;
import com.project.server.dto.AuthDto;
import com.project.server.dto.ActionDto;
import com.project.server.dto.WatchAssetDto;
import com.project.server.service.auth.WatchAssetSelectionService;

import jakarta.validation.Valid;

import com.project.server.service.auth.UserPreferenceService;
import com.project.server.service.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/users")
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
            @PathVariable("userId") Long userId
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

    // 사용자 닉네임 변경 (이전 AuthController에서 이동)
    @PatchMapping("/{userId}/nickname")
    public ResponseEntity<AuthDto.AuthResponse> updateNickname(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody AuthDto.UpdateNicknameRequest request) {
        AuthDto.AuthResponse response = authService.updateNickname(userId, request.getNickname());
        return ResponseEntity.ok(response);
    }

    // 계정 삭제 (이전 AuthController에서 이동)
    @DeleteMapping("/{userId}")
    public ResponseEntity<AuthDto.AuthResponse> deleteAccount(@PathVariable Long userId) {
        AuthDto.AuthResponse response = authService.deleteAccount(userId);
        return ResponseEntity.ok(response);
    }

    // 비밀번호 변경 (이전 AuthController에서 이동)
    @PatchMapping("/{userId}/password")
    public ResponseEntity<AuthDto.AuthResponse> changePassword(
            @PathVariable Long userId,
            @Valid @RequestBody AuthDto.ChangePasswordRequest request) {
        AuthDto.AuthResponse response = authService.changePassword(userId, request.getCurrentPassword(),
                request.getNewPassword());
        return ResponseEntity.ok(response);
    }
}
