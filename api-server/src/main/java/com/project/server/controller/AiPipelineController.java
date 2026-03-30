package com.project.server.controller;

import com.project.server.dto.ActionDto;
import com.project.server.service.integration.AiPipelineTriggerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiPipelineController {

    private final AiPipelineTriggerService aiPipelineTriggerService;

    @PostMapping("/trigger")
    public ResponseEntity<ActionDto.ActionResponse> triggerAi(
            @RequestParam(name = "user_id", defaultValue = "1") Long userId
    ) {
        aiPipelineTriggerService.triggerAndUpdateFeatured(userId);
        return ResponseEntity.ok(ActionDto.ActionResponse.builder()
                .message("AI 파이프라인 시뮬레이션 완료. 알림을 전송했습니다.")
                .build());
    }
}
