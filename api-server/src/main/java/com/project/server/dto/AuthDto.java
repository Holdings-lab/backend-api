package com.project.server.dto;

import lombok.Builder;
import lombok.Data;

public class AuthDto {

    @Data
    @Builder
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    @Builder
    public static class RegisterRequest {
        private String username;
        private String password;
    }

    @Data
    @Builder
    public static class AuthResponse {
        private Long userId;
        private String username;
        private String message;
    }

    @Data
    @Builder
    public static class FCMTokenRequest {
        private Long userId;
        private String fcmToken;
    }

    @Data
    @Builder
    public static class AccountInfo {
        private Long userId;
        private String username;
    }
}