package com.project.server.controller;

import org.springframework.http.HttpStatus;
import com.project.server.dto.AuthDto;
import com.project.server.service.auth.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        AuthDto.AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResult> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        AuthDto.LoginResult loginResult = authService.login(request);
        return ResponseEntity.ok(loginResult);
    }

    @PostMapping("/register-fcm-token")
    public ResponseEntity<AuthDto.AuthResponse> registerFCMToken(@Valid @RequestBody AuthDto.FCMTokenRequest request) {
        AuthDto.AuthResponse response = authService.registerFCMToken(request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/users/{userId}/nickname")
    public ResponseEntity<AuthDto.AuthResponse> updateNickname(
            @PathVariable Long userId,
            @Valid @RequestBody AuthDto.UpdateNicknameRequest request
    ) {
        AuthDto.AuthResponse response = authService.updateNickname(userId, request.getNickname());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete/{userId}")
    public ResponseEntity<AuthDto.AuthResponse> deleteAccount(@PathVariable Long userId) {
        AuthDto.AuthResponse response = authService.deleteAccount(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{userId}/change-password")
    public ResponseEntity<AuthDto.AuthResponse> changePassword(
            @PathVariable Long userId,
            @Valid @RequestBody AuthDto.ChangePasswordRequest request
    ) {
        AuthDto.AuthResponse response = authService.changePassword(userId, request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AuthDto.AccountInfo>> getAccounts() {
        List<AuthDto.AccountInfo> accounts = authService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/oauth-login")
    public ResponseEntity<AuthDto.OAuthLoginResult> oauthLogin(@Valid @RequestBody AuthDto.OAuthLoginRequest request) {
        AuthDto.OAuthLoginResult result = authService.oauthLogin(request);
        HttpStatus status = result.isNewUser() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}