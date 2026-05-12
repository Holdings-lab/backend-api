package com.project.server.controller;

import com.project.server.dto.ActionDto;
import com.project.server.service.integration.AiPipelineTriggerService;
import com.project.server.service.integration.RegressionTrainingService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/ai")
@Validated
@RequiredArgsConstructor
public class AiPipelineController {

    private final AiPipelineTriggerService aiPipelineTriggerService;
    private final RegressionTrainingService regressionTrainingService;

    @PostMapping("/users/{userId}/trigger")
    public ResponseEntity<ActionDto.ActionResponse> triggerAi(
            @PathVariable @Positive Long userId
    ) {
        return ResponseEntity.ok(aiPipelineTriggerService.triggerAndUpdateFeatured(userId));
    }

    @PostMapping("/train-regression")
    public ResponseEntity<ActionDto.TrainRegressionResponse> trainRegression() {
        return ResponseEntity.ok(regressionTrainingService.runTrainRegression());
    }
}
