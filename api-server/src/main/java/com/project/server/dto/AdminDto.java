package com.project.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.List;

public class AdminDto {

    @Data
    @Builder
    public static class CreateAccountRequest {
        @NotBlank(message = "이메일은 필수입니다.")
        private String email;

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
        private String nickname;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;

        private String fcmToken;
    }

    @Data
    @Builder
    public static class CreateAccountResponse {
        private Long userId;
        private String email;
        private String nickname;
        private String message;
    }

    @Data
    @Builder
    public static class DeleteAccountRequest {
        @NotNull(message = "삭제할 사용자 ID는 필수입니다.")
        @Positive(message = "사용자 ID는 1 이상이어야 합니다.")
        private Long userId;
    }

    @Data
    @Builder
    public static class DeleteAccountResponse {
        private Long userId;
        private String email;
        private String message;
    }

    @Data
    @Builder
    public static class SendNotificationRequest {
        @NotNull(message = "대상 사용자 ID는 필수입니다.")
        private List<Long> userIds;

        @NotBlank(message = "제목은 필수입니다.")
        private String title;

        @NotBlank(message = "메시지 내용은 필수입니다.")
        private String message;

        private String deeplink;
    }

    @Data
    @Builder
    public static class SendNotificationResponse {
        private int successCount;
        private int failureCount;
        private String message;
    }

    @Data
    @Builder
    public static class ChangePasswordRequest {
        @NotNull(message = "대상 사용자 ID는 필수입니다.")
        @Positive(message = "사용자 ID는 1 이상이어야 합니다.")
        private Long userId;

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        private String newPassword;
    }

    @Data
    @Builder
    public static class UserDetailResponse {
        private Long userId;
        private String email;
        private String nickname;
        private String fcmToken;
        private Long createdAt;
        private Long updatedAt;
    }

    @Data
    @Builder
    public static class UserListResponse {
        private int totalCount;
        private List<UserDetailResponse> users;
    }
}
