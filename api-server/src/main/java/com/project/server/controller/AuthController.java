package com.project.server.controller;

import com.project.server.dto.AuthDto;
import com.project.server.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(@RequestBody AuthDto.RegisterRequest request) {
        try {
            AuthDto.AuthResponse response = authService.register(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("회원가입 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                AuthDto.AuthResponse.builder()
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(@RequestBody AuthDto.LoginRequest request) {
        try {
            AuthDto.AuthResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("로그인 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                AuthDto.AuthResponse.builder()
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    @PostMapping("/register-fcm-token")
    public ResponseEntity<AuthDto.AuthResponse> registerFCMToken(@RequestBody AuthDto.FCMTokenRequest request) {
        try {
            AuthDto.AuthResponse response = authService.registerFCMToken(request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("FCM 토큰 등록 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                AuthDto.AuthResponse.builder()
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<AuthDto.AuthResponse> deleteAccount(@PathVariable Long userId) {
        try {
            AuthDto.AuthResponse response = authService.deleteAccount(userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.warn("회원 탈퇴 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(
                AuthDto.AuthResponse.builder()
                    .message(e.getMessage())
                    .build()
            );
        }
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AuthDto.AccountInfo>> getAccounts() {
        try {
            List<AuthDto.AccountInfo> accounts = authService.getAllAccounts();
            return ResponseEntity.ok(accounts);
        } catch (RuntimeException e) {
            log.warn("계정 목록 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
}