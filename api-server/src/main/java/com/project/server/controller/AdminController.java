package com.project.server.controller;

import com.project.server.dto.AdminDto;
import com.project.server.service.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Admin API Controller
 * 관리자 전용 API로 계정 관리, 알림 전송 등의 기능을 수행합니다.
 * 
 * PATH: /admin/*
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminController {

    private final AdminService adminService;

    // ==================== 계정 관리 ====================

            /**
             * 계정 추가 (Email 검증 우회)
             * POST /admin/accounts/add
             */
            @PostMapping("/accounts/add")
    public ResponseEntity<AdminDto.CreateAccountResponse> createAccount(
            @Valid @RequestBody AdminDto.CreateAccountRequest request
    ) {
        log.info("[Admin] 계정 추가 요청: {}", request.getEmail());
        AdminDto.CreateAccountResponse response = adminService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

            /**
             * 계정 삭제
             * DELETE /admin/accounts/{userId}
             */
            @DeleteMapping("/accounts/{userId}")
    public ResponseEntity<AdminDto.DeleteAccountResponse> deleteAccount(
            @PathVariable Long userId
    ) {
        log.info("[Admin] 계정 삭제 요청: userId={}", userId);
        AdminDto.DeleteAccountResponse response = adminService.deleteAccount(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 목록 조회
     * GET /admin/accounts
     */
    @GetMapping("/accounts")
    public ResponseEntity<AdminDto.UserListResponse> getUserList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        log.info("[Admin] 사용자 목록 조회");
        AdminDto.UserListResponse response = adminService.getUserList(page, size);
        return ResponseEntity.ok(response);
    }

    /**
     * 사용자 FCM 토큰 업데이트
     * PATCH /admin/accounts/{userId}/fcm-token
     */
    @PatchMapping("/accounts/{userId}/fcm-token")
    public ResponseEntity<AdminDto.CreateAccountResponse> updateFcmToken(
            @PathVariable Long userId,
            @RequestParam String fcmToken
    ) {
        log.info("[Admin] FCM 토큰 업데이트 요청: userId={}", userId);
        AdminDto.CreateAccountResponse response = adminService.updateUserFcmToken(userId, fcmToken);
        return ResponseEntity.ok(response);
    }

    /**
     * 비밀번호 변경 (관리자가 특정 사용자의 비밀번호 변경)
     * POST /admin/accounts/change-password
     */
    @PostMapping("/accounts/change-password")
    public ResponseEntity<AdminDto.CreateAccountResponse> changePassword(
            @Valid @RequestBody AdminDto.ChangePasswordRequest request
    ) {
        log.info("[Admin] 비밀번호 변경 요청: userId={}", request.getUserId());
        AdminDto.CreateAccountResponse response = adminService.changePassword(request.getUserId(), request.getNewPassword());
        return ResponseEntity.ok(response);
    }

    // ==================== 알림 관리 ====================

            /**
             * 특정 메시지로 알림 전송
             * POST /admin/notifications/send
             *
             * userIds가 null 또는 empty면 모든 사용자에게 전송
             */
            @PostMapping("/notifications/send")
    public ResponseEntity<AdminDto.SendNotificationResponse> sendNotification(
            @Valid @RequestBody AdminDto.SendNotificationRequest request
    ) {
        log.info("[Admin] 알림 전송 요청: title={}, userCount={}", 
                request.getTitle(), 
                request.getUserIds() != null ? request.getUserIds().size() : "all");
        AdminDto.SendNotificationResponse response = adminService.sendNotification(request);
        return ResponseEntity.ok(response);
    }

    // ==================== 상태 확인 ====================

    /**
     * Admin API 상태 확인
     * GET /admin/health
     */
    @GetMapping("/health")
        public ResponseEntity<Map<String, String>> health() {
                return ResponseEntity.ok(Map.of("status", "Admin API is running"));
    }
}
