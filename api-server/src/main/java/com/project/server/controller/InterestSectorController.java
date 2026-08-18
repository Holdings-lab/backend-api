package com.project.server.controller;

import com.project.server.dto.UserPreferenceDto;
import com.project.server.service.asset.InterestSectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관심 섹터 카탈로그(옵션 목록). 선택 저장은 /api/me/settings/interests.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/interest-sectors")
@RequiredArgsConstructor
public class InterestSectorController {

    private final InterestSectorService interestSectorService;

    @GetMapping("/options")
    public ResponseEntity<UserPreferenceDto.InterestOptionsResponse> getOptions() {
        return ResponseEntity.ok(interestSectorService.getOptions());
    }
}
