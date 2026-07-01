package com.project.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public interface HyphenApiClient {

    Map<String, String> BROKER_BANK_CODES = Map.of(
            "KIS", "0243",
            "NH", "0247",
            "KB", "0240",
            "MIRAE", "0238",
            "SAMSUNG", "0242",
            "KIWOOM", "0264");

    record HyphenCredential(
            String userId,
            String userPw,
            String loginMethod,
            String loginRequired,
            String accountPassword) {
    }

    JsonNode fetchAccountList(HyphenCredential credential, String brokerName);

    JsonNode fetchCashBalance(HyphenCredential credential, String brokerName, String accountNumber);

    JsonNode fetchHoldings(HyphenCredential credential, String brokerName, String accountNumber);

    JsonNode fetchDepositWithdrawHistory(
            HyphenCredential credential,
            String brokerName,
            String accountNumber,
            String fromDate,
            String toDate);

    JsonNode fetchAssetTransactionHistory(
            HyphenCredential credential,
            String brokerName,
            String accountNumber,
            String fromDate,
            String toDate);
}
