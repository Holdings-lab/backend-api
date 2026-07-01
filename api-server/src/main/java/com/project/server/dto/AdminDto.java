package com.project.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class AdminDto {

    @Data
    @Builder
    @NoArgsConstructor // Jackson을 위한 기본 생성자 추가
    @AllArgsConstructor // Builder를 위한 전체 생성자 추가
    public static class CreateUserRequest {
        @NotBlank(message = "이메일은 필수입니다.")
        private String email;

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하여야 합니다.")
        private String nickname;

        @NotBlank(message = "비밀번호는 필수입니다.")
        private String password;

        @Builder.Default
        private String fcmToken = "";
    }

    @Data
    @Builder
    public static class CreateUserResponse {
        private Long userId;
        private String email;
        private String nickname;
        private String fcmToken;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateFcmTokenRequest {
        @NotBlank(message = "FCM 토큰은 필수입니다.")
        private String fcmToken;
    }

    @Data
    @Builder
    public static class DeleteUserRequest {
        @NotNull(message = "삭제할 사용자 ID는 필수입니다.")
        @Positive(message = "사용자 ID는 1 이상이어야 합니다.")
        private Long userId;
    }

    @Data
    @Builder
    public static class DeleteUserResponse {
        private Long userId;
        private String email;
    }

    @Data
    @Builder
    public static class SendNotificationRequest {
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
    }

    @Data
    @Builder
    public static class ChangePasswordRequest {
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetAccountDetailsRequest {
        private java.math.BigDecimal cashBalance;
        private java.math.BigDecimal totalPurchaseAmount;
        private java.math.BigDecimal totalValuationAmount;
        private java.math.BigDecimal totalValuationGainLoss;
        private java.math.BigDecimal totalProfitRate;
        private java.math.BigDecimal estimatedDepositAsset;

        private String accountDisplay;
        private String accountName;
        private String accountNick;
        private String balance;
        private String currencyCode;
        private String availableBalance;

        private List<PortfolioPosition> positions;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PortfolioPosition {
            private String itemCode;
            private String itemName;
            private String productType;
            private String productCode;
            private java.math.BigDecimal quantity;
            private java.math.BigDecimal purchaseUnitPrice;
            private java.math.BigDecimal presentPrice;
        }

        // 최소 1개 이상의 파라미터가 있는지 검증
        public boolean hasAnyParameter() {
            return cashBalance != null || totalPurchaseAmount != null || totalValuationAmount != null
                    || totalValuationGainLoss != null || totalProfitRate != null || estimatedDepositAsset != null
                    || accountDisplay != null || accountName != null || accountNick != null || balance != null
                    || currencyCode != null || availableBalance != null
                    || (positions != null && !positions.isEmpty());
        }
    }
}
