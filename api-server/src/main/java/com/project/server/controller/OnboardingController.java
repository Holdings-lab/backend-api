package com.project.server.controller;

import com.project.server.dto.OnboardingDto;
import com.project.server.security.CurrentUserId;
import com.project.server.service.onboarding.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PatchMapping("/profile")
    public ResponseEntity<OnboardingDto.ProfileData> updateProfile(
            @CurrentUserId Long userId,
            @RequestBody OnboardingDto.UpdateProfileRequest request) {
        return ResponseEntity.ok(onboardingService.updateProfile(userId, request));
    }

    @GetMapping("/status")
    public ResponseEntity<OnboardingDto.StatusResponse> getStatus(@CurrentUserId Long userId) {
        return ResponseEntity.ok(onboardingService.getStatus(userId));
    }

    @PostMapping("/account/skip")
    public ResponseEntity<Void> skipAccount(@CurrentUserId Long userId) {
        onboardingService.skipAccountLink(userId);
        return ResponseEntity.ok().build();
    }
}
