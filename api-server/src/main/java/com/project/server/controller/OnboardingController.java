package com.project.server.controller;

import com.project.server.dto.OnboardingDto;
import com.project.server.service.onboarding.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PatchMapping("/{userId}/onboarding/profile")
    public ResponseEntity<OnboardingDto.ProfileData> updateProfile(
            @PathVariable Long userId,
            @RequestBody OnboardingDto.UpdateProfileRequest request) {
        return ResponseEntity.ok(onboardingService.updateProfile(userId, request));
    }

    @GetMapping("/{userId}/onboarding/status")
    public ResponseEntity<OnboardingDto.StatusResponse> getStatus(@PathVariable Long userId) {
        return ResponseEntity.ok(onboardingService.getStatus(userId));
    }

    @PostMapping("/{userId}/onboarding/account/skip")
    public ResponseEntity<Void> skipAccount(@PathVariable Long userId) {
        onboardingService.skipAccountLink(userId);
        return ResponseEntity.ok().build();
    }
}
