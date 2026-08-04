package com.project.server.controller;

import com.project.server.dto.OnboardingDto;
import com.project.server.service.onboarding.OnboardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brokers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BrokerController {

    private final OnboardingService onboardingService;

    @GetMapping
    public ResponseEntity<OnboardingDto.BrokerListResponse> listBrokers() {
        return ResponseEntity.ok(onboardingService.listBrokers());
    }
}
