package com.project.server.controller;

import com.project.server.dto.UserAssetDto;
import com.project.server.security.CurrentUserId;
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
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserAssetController {

    private final DailyBriefingService dailyBriefingService;
    private final HoldingsService holdingsService;
    private final GoalService goalService;
    private final UserSessionService userSessionService;
    private final InvestmentProfileService investmentProfileService;

    @GetMapping("/daily-briefing")
    public ResponseEntity<UserAssetDto.DailyBriefingResponse> getDailyBriefing(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ResponseEntity.ok(dailyBriefingService.getDailyBriefing(userId, refresh));
    }

    @GetMapping("/holdings")
    public ResponseEntity<UserAssetDto.HoldingsResponse> getHoldings(@CurrentUserId Long userId) {
        return ResponseEntity.ok(holdingsService.getHoldings(userId));
    }

    @PatchMapping("/goal")
    public ResponseEntity<UserAssetDto.GoalResponse> updateGoal(
            @CurrentUserId Long userId,
            @RequestBody UserAssetDto.UpdateGoalRequest request) {
        return ResponseEntity.ok(goalService.updateGoal(userId, request));
    }

    @GetMapping("/goal-progress")
    public ResponseEntity<UserAssetDto.GoalProgressResponse> getGoalProgress(@CurrentUserId Long userId) {
        return ResponseEntity.ok(goalService.getGoalProgress(userId));
    }

    @PostMapping("/session/heartbeat")
    public ResponseEntity<UserAssetDto.SessionHeartbeatResponse> heartbeat(
            @CurrentUserId Long userId,
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

    @PostMapping("/session/terminate")
    public ResponseEntity<Void> terminateSession(@CurrentUserId Long userId) {
        userSessionService.terminate(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/investment-profile")
    public ResponseEntity<UserAssetDto.InvestmentProfileResponse> getInvestmentProfile(@CurrentUserId Long userId) {
        return ResponseEntity.ok(investmentProfileService.getProfile(userId));
    }

    @PatchMapping("/investment-profile")
    public ResponseEntity<UserAssetDto.InvestmentProfileResponse> updateInvestmentProfile(
            @CurrentUserId Long userId,
            @RequestBody UserAssetDto.UpdateInvestmentProfileRequest request) {
        return ResponseEntity.ok(investmentProfileService.updateProfile(userId, request));
    }
}
