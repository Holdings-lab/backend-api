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
     * 증권사 계좌 연동 요청.
     * path의 userId는 앱 사용자 ID, body의 hyphenUserId는 증권사 로그인 ID.
     */
    public static class LinkRequest {
        private String hyphenUserId;
        private String hyphenUserPw;
        private String hyphenLoginMethod; // ID, CERT
        private String hyphenLoginRequired; // Y, N
        private String hyphenAccountPassword;
        private List<String> brokerNames;
    }

    /** 하이픈 in0104000534 전계좌조회 계좌 1건 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HyphenAccountSnapshot {
        private String accountDisplay;
        private String accountName;
        private String accountNick;
        private String openDate;
        private String endDate;
        private String lastTradeDate;
        private String balance;
        private String currencyCode;
        private String dormantYn;
        private String availableBalance;
        private String accountHolder;
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
        private HyphenAccountSnapshot hyphenAccount;
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
        private HyphenAccountSnapshot hyphenAccount;
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

    /** 하이픈 in0104000539 계좌 요약 + in0104000536 curBal */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountBalanceDto {
        private Long id;
        private BigDecimal estimatedDepositAsset;
        private BigDecimal cashBalance;
        private BigDecimal totalPurchaseAmount;
        private BigDecimal totalValuationAmount;
        private BigDecimal totalValuationGainLoss;
        private BigDecimal totalProfitRate;
        private LocalDate asOfDate;
        private LocalDateTime lastSyncedAt;
    }

    /** 하이픈 in0104000539 itemDetail */
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
        private BigDecimal purchaseUnitPrice;
        private BigDecimal presentPrice;
        private BigDecimal valuationAmount;
        private BigDecimal purchaseAmount;
        private BigDecimal valuationGainLoss;
        private BigDecimal profitRate;
        private String currencyCode;
        private String overseasYn;
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
        private BigDecimal estimatedDepositAsset;
        private BigDecimal cashBalance;
        private List<AssetPositionDto> positions;
        private LocalDateTime lastSyncedAt;
    }
}
