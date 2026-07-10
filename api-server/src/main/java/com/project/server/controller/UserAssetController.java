package com.project.server.controller;

import com.project.server.dto.UserAssetDto;
import com.project.server.service.asset.DailyBriefingService;
import com.project.server.service.asset.GoalService;
import com.project.server.service.asset.HoldingsService;
import com.project.server.service.asset.session.UserSessionService;
import com.project.server.service.asset.sync.UserSyncResult;
import com.project.server.service.asset.InvestmentProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserAssetController {

    private final DailyBriefingService dailyBriefingService;
    private final HoldingsService holdingsService;
    private final GoalService goalService;
    private final UserSessionService userSessionService;
    private final InvestmentProfileService investmentProfileService;

    @GetMapping("/{userId}/daily-briefing")
    public ResponseEntity<UserAssetDto.DailyBriefingResponse> getDailyBriefing(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(dailyBriefingService.getDailyBriefing(userId, refresh));
    }

    @GetMapping("/{userId}/holdings")
    public ResponseEntity<UserAssetDto.HoldingsResponse> getHoldings(@PathVariable Long userId) {
        return ResponseEntity.ok(holdingsService.getHoldings(userId));
    }

    @PatchMapping("/{userId}/goal")
    public ResponseEntity<UserAssetDto.GoalResponse> updateGoal(
            @PathVariable Long userId,
            @RequestBody UserAssetDto.UpdateGoalRequest request) {
        return ResponseEntity.ok(goalService.updateGoal(userId, request));
    }

    @GetMapping("/{userId}/goal-progress")
    public ResponseEntity<UserAssetDto.GoalProgressResponse> getGoalProgress(@PathVariable Long userId) {
        return ResponseEntity.ok(goalService.getGoalProgress(userId));
    }

    @PostMapping("/{userId}/session/heartbeat")
    public ResponseEntity<UserAssetDto.SessionHeartbeatResponse> heartbeat(
            @PathVariable Long userId,
            @RequestBody(required = false) UserAssetDto.SessionHeartbeatRequest request,
            @RequestParam(defaultValue = "false") boolean appOpen) {
        String deviceId = request != null ? request.getDeviceId() : null;
        boolean shouldSyncOnOpen = appOpen || (request != null && Boolean.TRUE.equals(request.getAppOpen()));
        UserSyncResult syncResult = userSessionService.heartbeat(userId, deviceId, shouldSyncOnOpen);
        return ResponseEntity.ok(UserAssetDto.SessionHeartbeatResponse.builder()
                .syncStatus(syncResult.success() ? "SUCCESS"
                        : syncResult.skipped() ? "SKIPPED" : "FAILED")
                .syncReason(syncResult.reason())
                .build());
    }

    @PostMapping("/{userId}/session/terminate")
    public ResponseEntity<Void> terminateSession(@PathVariable Long userId) {
        userSessionService.terminate(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{userId}/investment-profile")
    public ResponseEntity<UserAssetDto.InvestmentProfileResponse> getInvestmentProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(investmentProfileService.getProfile(userId));
    }

    @PatchMapping("/{userId}/investment-profile")
    public ResponseEntity<UserAssetDto.InvestmentProfileResponse> updateInvestmentProfile(
            @PathVariable Long userId,
            @RequestBody UserAssetDto.UpdateInvestmentProfileRequest request) {
        return ResponseEntity.ok(investmentProfileService.updateProfile(userId, request));
    }
}
