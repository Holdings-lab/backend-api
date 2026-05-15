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

    @PostMapping("/email/send-code")
    public ResponseEntity<AuthDto.EmailVerificationResponse> sendEmailVerificationCode(
            @Valid @RequestBody AuthDto.EmailCodeSendRequest request) {
        AuthDto.EmailVerificationResponse response = authService.sendEmailVerificationCode(request.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/email/verify-code")
    public ResponseEntity<AuthDto.EmailVerificationResponse> verifyEmailCode(
            @Valid @RequestBody AuthDto.EmailCodeVerifyRequest request) {
        AuthDto.EmailVerificationResponse response = authService.verifyEmailCode(request.getEmail(),
                request.getVerificationCode());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthDto.AuthResponse> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        AuthDto.AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.LoginResult> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        AuthDto.LoginResult loginResult = authService.login(request);
        return ResponseEntity.ok(loginResult);
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<AuthDto.AuthResponse> registerFCMToken(@Valid @RequestBody AuthDto.FCMTokenRequest request) {
        AuthDto.AuthResponse response = authService.registerFCMToken(request);
        return ResponseEntity.ok(response);
    }

    // user-scoped actions (nickname/change-password/delete) are moved to UserPreferenceController

    @GetMapping("/accounts")
    public ResponseEntity<List<AuthDto.AccountInfo>> getAccounts() {
        List<AuthDto.AccountInfo> accounts = authService.getAllAccounts();
        return ResponseEntity.ok(accounts);
    }

    @PostMapping("/login/oauth")
    public ResponseEntity<AuthDto.OAuthLoginResult> oauthLogin(@Valid @RequestBody AuthDto.OAuthLoginRequest request) {
        AuthDto.OAuthLoginResult result = authService.oauthLogin(request);
        HttpStatus status = result.isNewUser() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}