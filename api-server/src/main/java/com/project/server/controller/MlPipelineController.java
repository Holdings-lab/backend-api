package com.project.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.project.server.dto.ActionDto;
import com.project.server.security.CurrentUserId;
import com.project.server.service.integration.MlPipelineTriggerService;
import com.project.server.service.integration.MlSignalProxyService;
import com.project.server.service.integration.RegressionTrainingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/ml")
@Validated
@RequiredArgsConstructor
public class MlPipelineController {

    private final MlPipelineTriggerService mlPipelineTriggerService;
    private final RegressionTrainingService regressionTrainingService;
    private final MlSignalProxyService mlSignalProxyService;

    @PostMapping("/sync")
    public ResponseEntity<ActionDto.ActionResponse> triggerMl(
            @CurrentUserId Long userId
    ) {
        return ResponseEntity.ok(mlPipelineTriggerService.triggerAndUpdateFeatured(userId));
    }

    @PostMapping("/models/regression/training")
    public ResponseEntity<ActionDto.TrainRegressionResponse> trainRegression() {
        return ResponseEntity.ok(regressionTrainingService.runTrainRegression());
    }

    @PostMapping("/signal")
    public ResponseEntity<JsonNode> runSignal(
            @RequestParam(defaultValue = "QQQ") String ticker
    ) {
        return ResponseEntity.ok(mlSignalProxyService.runSignal(ticker));
    }
}
