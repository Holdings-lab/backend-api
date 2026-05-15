package com.project.server.controller;

import com.project.server.dto.AdminDto;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.service.admin.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
         * POST /admin/users
         */
        @PostMapping("/users")
        public ResponseEntity<AdminDto.CreateUserResponse> createUser(
                        @Valid @RequestBody AdminDto.CreateUserRequest request) {
                log.info("[Admin] 계정 추가 요청: {}", request.getEmail());
                AdminDto.CreateUserResponse response = adminService.createUser(request);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        /**
         * 계정 삭제
         * DELETE /admin/users/{userId}
         */
        @DeleteMapping("/users/{userId}")
        public ResponseEntity<AdminDto.DeleteUserResponse> deleteUser(
                        @PathVariable Long userId) {
                log.info("[Admin] 계정 삭제 요청: userId={}", userId);
                AdminDto.DeleteUserResponse response = adminService.deleteUser(userId);
                return ResponseEntity.ok(response);
        }

        /**
         * 사용자 목록 조회
         * GET /admin/users
         */
        @GetMapping("/users")
        public ResponseEntity<AdminDto.UserListResponse> getUserList(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "100") int size) {
                log.info("[Admin] 사용자 목록 조회");
                AdminDto.UserListResponse response = adminService.getUserList(page, size);
                return ResponseEntity.ok(response);
        }

        /**
         * 사용자 FCM 토큰 업데이트
         * PATCH /admin/users/{userId}/fcm-token
         */
        @PatchMapping("/users/{userId}/fcm-token")
        public ResponseEntity<AdminDto.CreateUserResponse> updateFcmToken(
                        @PathVariable Long userId,
                        @Valid @RequestBody AdminDto.UpdateFcmTokenRequest request) {
                log.info("[Admin] FCM 토큰 업데이트 요청: userId={}", userId);
                AdminDto.CreateUserResponse response = adminService.updateUserFcmToken(userId, request.getFcmToken());
                return ResponseEntity.ok(response);
        }

        /**
         * 비밀번호 변경 (관리자가 특정 사용자의 비밀번호 변경)
         * PATCH /admin/users/{userId}/password
         */
        @PatchMapping("/users/{userId}/password")
        public ResponseEntity<AdminDto.CreateUserResponse> changePassword(
                        @PathVariable Long userId,
                        @Valid @RequestBody AdminDto.ChangePasswordRequest request) {
                log.info("[Admin] 비밀번호 변경 요청: userId={}", userId);
                AdminDto.CreateUserResponse response = adminService.changePassword(userId,
                                request.getNewPassword());
                return ResponseEntity.ok(response);
        }

        // ==================== 알림 관리 ====================

        /**
         * 특정 메시지로 알림 전송
         * POST /admin/notifications
         *
         * userIds가 null 또는 empty면 모든 사용자에게 전송
         */
        @PostMapping("/notifications")
        public ResponseEntity<AdminDto.SendNotificationResponse> sendNotification(
                        @Valid @RequestBody AdminDto.SendNotificationRequest request) {
                log.info("[Admin] 알림 전송 요청: title={}, userCount={}",
                                request.getTitle(),
                                request.getUserIds() != null ? request.getUserIds().size() : "all");
                AdminDto.SendNotificationResponse response = adminService.sendNotification(request);
                return ResponseEntity.ok(response);
        }

        // ==================== 상태 확인 ====================

        // ==================== 테스트 및 모의 데이터 ====================

        /**
         * 계좌 상세 정보 및 포트폴리오 데이터 설정
         * PUT /admin/accounts/{accountId}
         */
        @PutMapping("/accounts/{accountId}")
        public ResponseEntity<BrokerAccountDto.BrokerAccountDetailResponse> setAccountDetails(
                        @PathVariable Long accountId,
                        @Valid @RequestBody AdminDto.SetAccountDetailsRequest request) {
                log.info("[Admin] 계좌 상세 정보 설정 요청: accountId={}", accountId);
                BrokerAccountDto.BrokerAccountDetailResponse response = adminService.updateAccountDetails(accountId,
                                request);
                return ResponseEntity.ok(response);
        }
}
