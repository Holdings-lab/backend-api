package com.project.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class BrokerAccountDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkRequest {
        private String brokerName; // "KIS"
        private String accountNumber; // 선택사항
        // 간편인증(Direct) 연동 시 필수; OAuth 연동 시 null/empty
        private String connectedId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthCallbackRequest {
        private String code;
        private String state;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthResponse {
        private String authUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerAccountResponse {
        private Long accountId;
        private String brokerName;
        private String accountNumber;
        private String accountNickname;
        private String accountOwnerName;
        private String status;
        private Boolean isPrimary;
        private LocalDateTime lastSyncedAt;
        private Integer syncCount;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerAccountDetailResponse {
        private Long accountId;
        private String brokerName;
        private String accountNumber;
        private String accountNickname;
        private String accountOwnerName;
        private String accountType;
        private String status;
        private Boolean isPrimary;
        private AccountBalanceDto latestBalance;
        private List<AssetPositionDto> positions;
        
        // CODEF 응답 필드
        private String accountDisplay;
        private String principal;
        private String purchaseAmount;
        private String valuationAmt;
        private String valuationPL;
        private String earningsRate;
        private String depositReceived;
        private String depositReceivedD1;
        private String depositReceivedD2;
        private String depositReceivedF;
        private String withdrawalAmt;
        private String loanAmt;

        private LocalDateTime lastSyncedAt;
        private Integer syncCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnlinkAccountResponse {
        private String brokerName;
        private String resAccountName;
        private String resAccount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalanceDto {
        private Long id;
        private BigDecimal totalAssetValue;
        private BigDecimal cashBalance;
        private BigDecimal depositAmount;
        private BigDecimal evaluationAmount;
        private BigDecimal gainLoss;
        private BigDecimal gainLossRate;
        private BigDecimal dailyGainLoss;
        private BigDecimal dailyGainLossRate;
        private LocalDate asOfDate;
        private LocalDateTime lastSyncedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetPositionDto {
        private String symbol;
        private String positionType;
        private BigDecimal quantity;
        private BigDecimal purchasePrice;
        private BigDecimal currentPrice;
        private BigDecimal currentValue;
        private BigDecimal purchaseAmount;
        private BigDecimal gainLoss;
        private BigDecimal gainLossRate;
        private String currencyCode;
        private LocalDate purchasedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncRequest {
        private String syncType; // BALANCE, POSITION, HISTORY, ALL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncResponse {
        private String syncId;
        private String status;
        private String syncType;
        private LocalDateTime startedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncStatusResponse {
        private String syncId;
        private String status;
        private String syncType;
        private Integer recordCount;
        private Integer syncDurationMs;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncHistoryResponse {
        private Long id;
        private String syncType;
        private String status;
        private Integer recordCount;
        private Integer syncDurationMs;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CombinedPortfolioResponse {
        private BigDecimal totalAssetValue;
        private BigDecimal cashBalance;
        private BigDecimal depositAmount;
        private BigDecimal evaluationAmount;
        private BigDecimal gainLoss;
        private BigDecimal gainLossRate;
        private BigDecimal dailyGainLoss;
        private BigDecimal dailyGainLossRate;
        private List<AssetPositionDto> positions;
        private Map<String, AccountPortfolioDto> byBroker;
        private LocalDateTime lastSyncedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountPortfolioDto {
        private Long accountId;
        private String accountNumber;
        private String brokerName;
        private BigDecimal totalAssetValue;
        private BigDecimal cashBalance;
        private List<AssetPositionDto> positions;
        private LocalDateTime lastSyncedAt;
    }
}
