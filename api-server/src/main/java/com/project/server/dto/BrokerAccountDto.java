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
    /**
     * 한투 계좌 연동 요청.
     * appKey/appSecret이 없으면 KIS_MOCK_* env를 사용.
     */
    public static class LinkRequest {
        private String brokerName;
        private List<String> brokerNames;
        private String appKey;
        private String appSecret;
        private String accountNumber;
        private String accountProductCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CredentialsUpdateRequest {
        private String appKey;
        private String appSecret;
        private String accountNumber;
        private String accountProductCode;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountSnapshot {
        private String accountDisplay;
        private String accountName;
        private String accountNick;
        private String balance;
        private String currencyCode;
        private String availableBalance;
        private String accountHolder;
        private String accountProductCode;
        private String cano;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerAccountResponse {
        /** 앱 내부 연동 계좌 PK */
        private Long accountId;
        private String brokerName;
        private String accountNumber;
        private String accountNickname;
        private String accountOwnerName;
        private String accountType;
        private String status;
        private Boolean isPrimary;
        private AccountSnapshot accountSnapshot;
        private String credentialSource;
        private Boolean hasCredentials;
        private LocalDateTime lastSyncedAt;
        private Integer syncCount;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerAccountDetailResponse {
        /** 앱 내부 연동 계좌 PK */
        private Long accountId;
        private String brokerName;
        private String accountNumber;
        private String accountNickname;
        private String accountOwnerName;
        private String accountType;
        private String status;
        private Boolean isPrimary;
        private AccountSnapshot accountSnapshot;
        private String credentialSource;
        private Boolean hasCredentials;
        private AccountBalanceDto latestBalance;
        private List<AssetPositionDto> positions;
        private LocalDateTime lastSyncedAt;
        private Integer syncCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleAccountInfo {
        private Long accountId;
        private String brokerName;
        private String accountNumber;
        private String accountNickname;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnlinkAccountResponse {
        private Long accountId;
        private String brokerName;
        private String accountNumber;
        private String accountNickname;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SetPrimaryAccountResponse {
        private Long accountId;
        private String brokerName;
        private String accountNumber;
        private String accountNickname;
        private SimpleAccountInfo previousPrimaryAccount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalanceDto {
        private Long id;
        private String currencyCode;
        private Map<String, BigDecimal> fxRates;
        private BigDecimal estimatedDepositAsset;
        private BigDecimal cashBalance;
        private BigDecimal totalPurchaseAmount;
        private BigDecimal totalValuationAmount;
        private BigDecimal totalValuationGainLoss;
        private BigDecimal totalProfitRate;
        private LocalDate asOfDate;
        private LocalDateTime lastSyncedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionNativeDto {
        private BigDecimal purchaseUnitPrice;
        private BigDecimal presentPrice;
        private BigDecimal purchaseAmount;
        private BigDecimal valuationAmount;
        private BigDecimal gainLoss;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionKrwDto {
        private BigDecimal purchaseAmount;
        private BigDecimal valuationAmount;
        private BigDecimal gainLoss;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetPositionDto {
        private String itemCode;
        private String itemName;
        private String productType;
        private String productCode;
        private BigDecimal quantity;
        private BigDecimal profitRate;
        private String currencyCode;
        private String overseasYn;
        private BigDecimal fxRate;
        @com.fasterxml.jackson.annotation.JsonProperty("native")
        private PositionNativeDto nativeAmounts;
        private PositionKrwDto krw;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncRequest {
        private String syncType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncResponse {
        private Long syncId;
        private String status;
        private String syncType;
        private LocalDateTime startedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncStatusResponse {
        private Long syncId;
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
        private String currencyCode;
        private Map<String, BigDecimal> fxRates;
        private BigDecimal estimatedDepositAsset;
        private BigDecimal cashBalance;
        private BigDecimal totalPurchaseAmount;
        private BigDecimal totalValuationAmount;
        private BigDecimal totalValuationGainLoss;
        private BigDecimal totalProfitRate;
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
        private String currencyCode;
        private Map<String, BigDecimal> fxRates;
        private BigDecimal estimatedDepositAsset;
        private BigDecimal cashBalance;
        private List<AssetPositionDto> positions;
        private LocalDateTime lastSyncedAt;
    }
}
