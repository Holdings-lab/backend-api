package com.project.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class AuthDto {

    @Data
    @Builder
    public static class LoginRequest {
        @NotBlank(message = "이메일은 필수입니다.")
        private String email;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;
    }

    @Data
    @Builder
    public static class RegisterRequest {
        private String email;
        private String nickname;
        private String password;
    }

    @Data
    @Builder
    public static class AuthResponse {
        private Long userId;
        private String email;
        private String nickname;
    }

    @Data
    @Builder
    public static class LoginResult {
        private Long userId;
        private String email;
        private String nickname;
        private String accessToken;
        private String refreshToken;
        private boolean onboardingCompleted;
    }

    @Data
    @Builder
    public static class FCMTokenRequest {
        @NotNull(message = "사용자 ID는 필수입니다.")
        @Positive(message = "사용자 ID는 1 이상이어야 합니다.")
        private Long userId;

        @NotBlank(message = "FCM 토큰은 필수입니다.")
        @Size(min = 10, max = 1000, message = "FCM 토큰 형식이 유효하지 않습니다.")
        private String fcmToken;
    }

    @Data
    @Builder
    public static class UpdateNicknameRequest {
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9가-힣 _-]+$", message = "닉네임은 한글, 영문, 숫자, 공백, _ - 만 사용할 수 있습니다.")
        private String nickname;
    }

    @Data
    @Builder
    public static class AccountInfo {
        private Long userId;
        private String email;
        private String nickname;
    }

    @Data
    @Builder
    public static class MeResponse {
        private Profile profile;
        private List<WatchAssetReturn> watchAssets;
        private String watchAssetsLinkText;
        private List<StudyStat> studyStats;
        private List<SettingMenuItem> settingsMenu;
    }

    @Data
    @Builder
    public static class Profile {
        private String avatarText;
        private String name;
        private String summaryText;
    }

    @Data
    @Builder
    public static class WatchAssetReturn {
        private String assetName;
        private double changePercent;
    }

    @Data
    @Builder
    public static class StudyStat {
        private String label;
        private String valueText;
    }

    @Data
    @Builder
    public static class SettingMenuItem {
        private String key;
        private String title;
        private String description;
    }

    @Data
    @Builder
    public static class ChangePasswordRequest {
        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        private String currentPassword;

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        private String newPassword;
    }

    @Data
    @Builder
    public static class OAuthLoginRequest {
        @NotBlank(message = "OAuth 제공자는 필수입니다. (apple, google, kakao)")
        private String provider;

        @NotBlank(message = "OAuth 인가 코드는 필수입니다.")
        private String authorizationCode;

        private String redirectUri;
    }

    @Data
    @Builder
    public static class OAuthLoginResult {
        private Long userId;
        private String email;
        private String nickname;
        private String accessToken;
        private String refreshToken;
        private boolean onboardingCompleted;
        private boolean newUser;
    }
}