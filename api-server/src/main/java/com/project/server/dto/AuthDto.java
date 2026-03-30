package com.project.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class AuthDto {

    @Data
    @Builder
    public static class LoginRequest {
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(min = 3, max = 50, message = "아이디는 3자 이상 50자 이하여야 합니다.")
        private String username;

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 4, max = 100, message = "비밀번호는 4자 이상 100자 이하여야 합니다.")
        private String password;
    }

    @Data
    @Builder
    public static class RegisterRequest {
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(min = 3, max = 50, message = "아이디는 3자 이상 50자 이하여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "아이디는 영문, 숫자, . _ - 만 사용할 수 있습니다.")
        private String username;

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z0-9가-힣 _-]+$", message = "닉네임은 한글, 영문, 숫자, 공백, _ - 만 사용할 수 있습니다.")
        private String nickname;

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 4, max = 100, message = "비밀번호는 4자 이상 100자 이하여야 합니다.")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
        private String password;
    }

    @Data
    @Builder
    public static class AuthResponse {
        private Long userId;
        private String username;
        private String nickname;
        private String message;
    }

    @Data
    @Builder
    public static class LoginResult {
        private Long userId;
        private String username;
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
        private String username;
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
}