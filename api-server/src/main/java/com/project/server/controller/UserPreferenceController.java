package com.project.server.controller;

import com.project.server.dto.UserPreferenceDto;
import com.project.server.dto.AuthDto;
import com.project.server.dto.ActionDto;
import com.project.server.dto.WatchAssetDto;
import com.project.server.security.CurrentUserId;
import com.project.server.service.auth.WatchAssetSelectionService;

import jakarta.validation.Valid;

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
    public ResponseEntity<AuthDto.MeResponse> getMe(@CurrentUserId Long userId) {
        return ResponseEntity.ok(authService.getMe(userId));
    }

    @GetMapping("/profile")
    public ResponseEntity<AuthDto.Profile> getMeProfile(@CurrentUserId Long userId) {
        return ResponseEntity.ok(authService.getMeProfile(userId));
    }

    @GetMapping("/watch-assets")
    public ResponseEntity<java.util.List<AuthDto.WatchAssetReturn>> getMeWatchAssets(@CurrentUserId Long userId) {
        return ResponseEntity.ok(authService.getMeWatchAssets(userId));
    }

    @GetMapping("/study-stats")
    public ResponseEntity<java.util.List<AuthDto.StudyStat>> getMeStudyStats(@CurrentUserId Long userId) {
        return ResponseEntity.ok(authService.getMeStudyStats(userId));
    }

    @GetMapping("/settings")
    public ResponseEntity<UserPreferenceDto.SettingsHomeResponse> getSettings(@CurrentUserId Long userId) {
        return ResponseEntity.ok(userPreferenceService.getSettingsHome(userId));
    }

    @PatchMapping("/settings/notifications")
    public ResponseEntity<UserPreferenceDto.NotificationSettingsResponse> updateNotificationSettings(
            @CurrentUserId Long userId,
            @RequestBody UserPreferenceDto.UpdateNotificationSettingsRequest request
    ) {
        return ResponseEntity.ok(userPreferenceService.updateNotificationSettings(userId, request));
    }

    @PostMapping("/notifications/test")
    public ResponseEntity<UserPreferenceDto.TestNotificationResponse> sendTestNotification(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(userPreferenceService.sendTestNotification(userId));
    }

    @PostMapping("/watch-assets")
    public ResponseEntity<ActionDto.ActionResponse> updateWatchAssets(
            @CurrentUserId Long userId,
            @RequestBody WatchAssetDto.UpdateWatchAssetsRequest request
    ) {
        watchAssetSelectionService.updateSelectedAssets(userId, request.getAssetNames());
        return ResponseEntity.ok(ActionDto.ActionResponse.builder().action("watch-assets-update").status("completed").build());
    }

    @PatchMapping("/nickname")
    public ResponseEntity<AuthDto.AuthResponse> updateNickname(
            @CurrentUserId Long userId,
            @Valid @RequestBody AuthDto.UpdateNicknameRequest request) {
        AuthDto.AuthResponse response = authService.updateNickname(userId, request.getNickname());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    public ResponseEntity<AuthDto.AuthResponse> deleteAccount(@CurrentUserId Long userId) {
        AuthDto.AuthResponse response = authService.deleteAccount(userId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/password")
    public ResponseEntity<AuthDto.AuthResponse> changePassword(
            @CurrentUserId Long userId,
            @Valid @RequestBody AuthDto.ChangePasswordRequest request) {
        AuthDto.AuthResponse response = authService.changePassword(userId, request.getCurrentPassword(),
                request.getNewPassword());
        return ResponseEntity.ok(response);
    }
}
