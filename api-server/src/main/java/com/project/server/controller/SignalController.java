package com.project.server.controller;

import com.project.server.service.event.SignalDetailService;
import com.project.server.exception.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/signals")
public class SignalController {
    private final SignalDetailService signalDetailService;

    public SignalController(SignalDetailService signalDetailService) {
        this.signalDetailService = signalDetailService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSignalDetail(@PathVariable("id") String idParam) {
        if (idParam == null || idParam.trim().isEmpty()) {
            throw ApiException.badRequest("올바르지 않은 시그널 ID입니다.", "SIGNAL_INVALID_ID");
        }

        // Parse: "EVT-001" 또는 "001" 형식 모두 허용
        String numericStr = idParam.trim();
        if (numericStr.startsWith("EVT-")) {
            numericStr = numericStr.substring(4);
        }

        long signalId;
        try {
            signalId = Long.parseLong(numericStr);
            if (signalId <= 0) {
                throw ApiException.badRequest("올바르지 않은 시그널 ID입니다.", "SIGNAL_INVALID_ID");
            }
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("올바르지 않은 시그널 ID입니다.", "SIGNAL_INVALID_ID");
        }

        List<String> mockAssets = Arrays.asList("QQQ", "AAPL");
        Map<String, Object> response = signalDetailService.getDynamicSignalDetail(idParam, mockAssets);
        return ResponseEntity.ok(response);
    }
}
