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
        // 직접 조작 가능한 파라미터들 (모두 선택사항, 최소 1개 이상 필요)
        // totalAssetValue는 제외 - 서버가 positions + cashBalance로부터 자동 계산
        private java.math.BigDecimal depositAmount;
        private java.math.BigDecimal cashBalance;
        private String principal;
        private String purchaseAmount;
        private String valuationAmt;
        private String depositReceived;
        private String depositReceivedD1;
        private String depositReceivedD2;
        private String depositReceivedF;
        private String withdrawalAmt;
        private String loanAmt;

        // 임의의 종목 데이터 리스트 (심볼, 수량, 매입가, 현재가)
        private List<PortfolioPosition> positions;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class PortfolioPosition {
            private String symbol;
            private String positionType; // STOCK, ETF 등
            private java.math.BigDecimal quantity;
            private java.math.BigDecimal purchasePrice;
            private java.math.BigDecimal currentPrice;
        }

        // 최소 1개 이상의 파라미터가 필요한지 검증
        public boolean hasAnyParameter() {
            return depositAmount != null || cashBalance != null ||
                   principal != null || purchaseAmount != null || valuationAmt != null ||
                   depositReceived != null || depositReceivedD1 != null || depositReceivedD2 != null ||
                   depositReceivedF != null || withdrawalAmt != null || loanAmt != null ||
                   (positions != null && !positions.isEmpty());
        }
    }
}
