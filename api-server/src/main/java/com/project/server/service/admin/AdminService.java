package com.project.server.service.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.project.server.domain.NotificationHistoryEntity;
import com.project.server.domain.UserEntity;
import com.project.server.domain.UserProfileEntity;
import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.AccountBalanceEntity;
import com.project.server.domain.AssetPositionEntity;
import com.project.server.dto.AdminDto;
import com.project.server.dto.BrokerAccountDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.NotificationHistoryRepository;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.UserProfileRepository;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.repository.AccountBalanceRepository;
import com.project.server.repository.AssetPositionRepository;
import com.project.server.service.broker.BrokerAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserJpaRepository userJpaRepository;
    private final NotificationHistoryRepository notificationHistoryRepository;
    private final UserProfileRepository userProfileRepository;
    private final BrokerAccountRepository brokerAccountRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final AssetPositionRepository assetPositionRepository;
    private final BrokerAccountService brokerAccountService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Transactional
    public AdminDto.CreateUserResponse createUser(AdminDto.CreateUserRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedNickname = request.getNickname().trim();

        if (userJpaRepository.findByEmail(normalizedEmail).isPresent()) {
            throw ApiException.conflict("이미 존재하는 이메일입니다.", "AUTH_EMAIL_DUPLICATED");
        }

        UserEntity newUser = UserEntity.builder()
                .email(normalizedEmail)
                .nickname(normalizedNickname)
                .password(passwordEncoder.encode(request.getPassword()))
                .fcmToken(request.getFcmToken())
                .build();

        UserEntity saved = userJpaRepository.save(newUser);

        // 프로필 자동 생성
        UserProfileEntity profile = UserProfileEntity.builder()
                .userId(saved.getId())
                .avatarText(deriveAvatarInitials(saved.getNickname()))
                .weeklyLearningCount(0)
                .quizAccuracyPercent(0)
                .weakTopic("없음")
                .build();
        userProfileRepository.save(profile);

        log.info("[Admin] 계정 추가: {}", normalizedEmail);

        return AdminDto.CreateUserResponse.builder()
                .userId(saved.getId())
                .email(saved.getEmail())
                .nickname(saved.getNickname())
                .fcmToken(saved.getFcmToken())
                .build();
    }

    @Transactional
    public AdminDto.DeleteUserResponse deleteUser(Long userId) {
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND"));

        String email = user.getEmail();
        userJpaRepository.deleteById(userId);
        log.info("[Admin] 계정 삭제: {}", email);

        return AdminDto.DeleteUserResponse.builder()
                .userId(userId)
                .email(email)
                .build();
    }

    @Transactional
    public AdminDto.SendNotificationResponse sendNotification(AdminDto.SendNotificationRequest request) {
        List<UserEntity> targetUsers;

        if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
            targetUsers = userJpaRepository.findAll();
            log.info("[Admin] 모든 사용자에게 알림 전송 시작: {}", targetUsers.size());
        } else {
            targetUsers = userJpaRepository.findAllById(request.getUserIds());
            log.info("[Admin] 특정 사용자에게 알림 전송 시작: {}", targetUsers.size());
        }

        int successCount = 0;
        int failureCount = 0;

        for (UserEntity user : targetUsers) {
            try {
                sendFcmMessage(user, request);
                recordNotificationHistory(user.getId(), request, "SENT", null, null);
                successCount++;
            } catch (Exception e) {
                String errorCode = resolveErrorCode(e);
                recordNotificationHistory(user.getId(), request, "FAILED", errorCode, e.getMessage());
                failureCount++;
                log.warn("[Admin] 알림 전송 실패: userId={}, error={}", user.getId(), e.getMessage());
            }
        }

        log.info("[Admin] 알림 전송 완료: 성공={}, 실패={}", successCount, failureCount);
        return AdminDto.SendNotificationResponse.builder()
                .successCount(successCount)
                .failureCount(failureCount)
                .build();
    }

    private void sendFcmMessage(UserEntity user, AdminDto.SendNotificationRequest request) {
        if (user.getFcmToken() == null || user.getFcmToken().trim().isEmpty()) {
            throw new RuntimeException("FCM 토큰이 없습니다.");
        }

        try {
            Message.Builder messageBuilder = Message.builder()
                    .setToken(user.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle(request.getTitle())
                            .setBody(request.getMessage())
                            .build());

            if (request.getDeeplink() != null && !request.getDeeplink().isEmpty()) {
                messageBuilder.putData("deeplink", request.getDeeplink());
            }

            FirebaseMessaging.getInstance().send(messageBuilder.build());
        } catch (Exception e) {
            throw new RuntimeException("FCM 전송 실패: " + e.getMessage(), e);
        }
    }

    @Transactional
    private void recordNotificationHistory(
            Long userId,
            AdminDto.SendNotificationRequest request,
            String status,
            String errorCode,
            String errorMessage) {
        NotificationHistoryEntity history = NotificationHistoryEntity.builder()
                .userId(userId)
                .title(request.getTitle())
                // 실무 운영 기준으로 payload 원문 대신 핵심 필드만 저장
                .message(null)
                .deeplink(null)
                .status(status)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .sentAt(LocalDateTime.now())
                .build();

        notificationHistoryRepository.save(history);
    }

    private String resolveErrorCode(Exception e) {
        if (e instanceof ApiException apiException) {
            return apiException.getErrorCode();
        }
        return "ADMIN_NOTIFICATION_SEND_FAILED";
    }

    public AdminDto.UserListResponse getUserList(int page, int size) {
        List<UserEntity> users = userJpaRepository.findAll();

        List<AdminDto.UserDetailResponse> userDetails = users.stream()
                .map(user -> AdminDto.UserDetailResponse.builder()
                        .userId(user.getId())
                        .email(user.getEmail())
                        .nickname(user.getNickname())
                        .fcmToken(user.getFcmToken())
                        .createdAt(0L)
                        .updatedAt(0L)
                        .build())
                .toList();

        return AdminDto.UserListResponse.builder()
                .totalCount(userDetails.size())
                .users(userDetails)
                .build();
    }

    @Transactional
    public AdminDto.CreateUserResponse updateUserFcmToken(Long userId, String fcmToken) {
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND"));

        user.setFcmToken(fcmToken);
        userJpaRepository.save(user);
        log.info("[Admin] FCM 토큰 업데이트: userId={}", userId);

        return AdminDto.CreateUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .fcmToken(user.getFcmToken())
                .build();
    }

    @Transactional
    public AdminDto.CreateUserResponse changePassword(Long userId, String newPassword) {
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userJpaRepository.save(user);
        log.info("[Admin] 비밀번호 변경: userId={}", userId);

        return AdminDto.CreateUserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .fcmToken(user.getFcmToken())
                .build();
    }

    private String deriveAvatarInitials(String name) {
        if (name == null || name.isBlank())
            return "";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        } else {
            String first = parts[0];
            String second = parts[parts.length - 1];
            String initials = "";
            if (!first.isBlank())
                initials += first.substring(0, 1);
            if (!second.isBlank())
                initials += second.substring(0, 1);
            return initials.toUpperCase();
        }
    }

    @Transactional
    public BrokerAccountDto.BrokerAccountDetailResponse updateAccountDetails(Long accountId,
            AdminDto.SetAccountDetailsRequest request) {
        BrokerAccountEntity account = brokerAccountRepository.findById(accountId)
                .orElseThrow(() -> ApiException.notFound("존재하지 않는 계좌입니다.", "ACCOUNT_NOT_FOUND"));

        // 기존 하이픈 응답 데이터를 가져와서 업데이트 (있으면)
        Map<String, Object> hyphenAccountDetails = new java.util.HashMap<>();
        if (account.getHyphenAccountDetails() != null && !account.getHyphenAccountDetails().isEmpty()) {
            try {
                hyphenAccountDetails = objectMapper.readValue(account.getHyphenAccountDetails(),
                        new TypeReference<Map<String, Object>>() {
                        });
            } catch (Exception e) {
                log.warn("Failed to parse existing hyphen account details", e);
            }
        }

        if (request.getAccountDisplay() != null) {
            hyphenAccountDetails.put("acctDisp", request.getAccountDisplay());
        }
        if (request.getAccountName() != null) {
            hyphenAccountDetails.put("acctNm", request.getAccountName());
        }
        if (request.getAccountNick() != null) {
            hyphenAccountDetails.put("acctNick", request.getAccountNick());
        }
        if (request.getBalance() != null) {
            hyphenAccountDetails.put("balance", request.getBalance());
        }
        if (request.getCurrencyCode() != null) {
            hyphenAccountDetails.put("curCd", request.getCurrencyCode());
        }
        if (request.getAvailableBalance() != null) {
            hyphenAccountDetails.put("ablBal", request.getAvailableBalance());
        }

        try {
            boolean hasPortfolioValues = request.getTotalPurchaseAmount() != null
                    || request.getTotalValuationAmount() != null
                    || request.getTotalValuationGainLoss() != null
                    || request.getTotalProfitRate() != null
                    || request.getEstimatedDepositAsset() != null
                    || request.getCashBalance() != null;
            boolean hasPositions = request.getPositions() != null && !request.getPositions().isEmpty();

            if (hasPortfolioValues || hasPositions) {
                accountBalanceRepository.deleteByAccountId(account.getId());
                assetPositionRepository.deleteByAccountId(account.getId());

                BigDecimal cashBalance = request.getCashBalance() != null ? request.getCashBalance() : BigDecimal.ZERO;

                BigDecimal derivedValuationAmount = BigDecimal.ZERO;
                BigDecimal derivedPurchaseAmount = BigDecimal.ZERO;
                BigDecimal derivedGainLoss = BigDecimal.ZERO;
                if (hasPositions) {
                    for (AdminDto.SetAccountDetailsRequest.PortfolioPosition pos : request.getPositions()) {
                        BigDecimal qty = pos.getQuantity() != null ? pos.getQuantity() : BigDecimal.ZERO;
                        BigDecimal presentPrice = pos.getPresentPrice() != null ? pos.getPresentPrice()
                                : BigDecimal.ZERO;
                        BigDecimal purchaseUnitPrice = pos.getPurchaseUnitPrice() != null ? pos.getPurchaseUnitPrice()
                                : BigDecimal.ZERO;
                        BigDecimal valuationAmount = qty.multiply(presentPrice);
                        BigDecimal purchaseAmount = qty.multiply(purchaseUnitPrice);
                        BigDecimal valuationGainLoss = valuationAmount.subtract(purchaseAmount);
                        BigDecimal profitRate = purchaseAmount.compareTo(BigDecimal.ZERO) > 0
                                ? valuationGainLoss.divide(purchaseAmount, 4, java.math.RoundingMode.HALF_UP)
                                        .multiply(new BigDecimal(100))
                                : BigDecimal.ZERO;

                        derivedValuationAmount = derivedValuationAmount.add(valuationAmount);
                        derivedPurchaseAmount = derivedPurchaseAmount.add(purchaseAmount);
                        derivedGainLoss = derivedGainLoss.add(valuationGainLoss);

                        String itemCode = pos.getItemCode() != null ? pos.getItemCode() : "UNKNOWN";
                        assetPositionRepository.save(AssetPositionEntity.builder()
                                .accountId(account.getId())
                                .userId(account.getUserId())
                                .symbol(itemCode)
                                .itemCode(itemCode)
                                .itemName(pos.getItemName())
                                .positionType(pos.getProductType() != null ? pos.getProductType() : "STOCK")
                                .productCode(pos.getProductCode())
                                .quantity(qty)
                                .currentPrice(presentPrice)
                                .purchasePrice(purchaseUnitPrice)
                                .currentValue(valuationAmount)
                                .purchaseAmount(purchaseAmount)
                                .gainLoss(valuationGainLoss)
                                .gainLossRate(profitRate)
                                .currencyCode("KRW")
                                .build());
                    }
                }

                BigDecimal totalPurchaseAmount = request.getTotalPurchaseAmount() != null
                        ? request.getTotalPurchaseAmount()
                        : derivedPurchaseAmount;
                BigDecimal totalValuationAmount = request.getTotalValuationAmount() != null
                        ? request.getTotalValuationAmount()
                        : derivedValuationAmount;
                BigDecimal totalValuationGainLoss = request.getTotalValuationGainLoss() != null
                        ? request.getTotalValuationGainLoss()
                        : derivedGainLoss;
                BigDecimal estimatedDepositAsset = request.getEstimatedDepositAsset() != null
                        ? request.getEstimatedDepositAsset()
                        : totalValuationAmount.add(cashBalance);
                BigDecimal totalProfitRate = request.getTotalProfitRate() != null
                        ? request.getTotalProfitRate()
                        : (totalPurchaseAmount.compareTo(BigDecimal.ZERO) > 0
                                ? totalValuationGainLoss.divide(totalPurchaseAmount, 4, java.math.RoundingMode.HALF_UP)
                                        .multiply(new BigDecimal(100))
                                : BigDecimal.ZERO);

                accountBalanceRepository.save(AccountBalanceEntity.builder()
                        .accountId(account.getId())
                        .userId(account.getUserId())
                        .totalAssetValue(estimatedDepositAsset)
                        .depositAmount(totalPurchaseAmount)
                        .cashBalance(cashBalance)
                        .evaluationAmount(totalValuationAmount)
                        .gainLoss(totalValuationGainLoss)
                        .gainLossRate(totalProfitRate)
                        .dailyGainLoss(BigDecimal.ZERO)
                        .dailyGainLossRate(BigDecimal.ZERO)
                        .asOfDate(LocalDate.now())
                        .lastSyncedAt(LocalDateTime.now())
                        .build());
            }

            // Map을 JSON 문자열로 변환해서 저장
            String hyphenAccountDetailsJson = objectMapper.writeValueAsString(hyphenAccountDetails);
            account.setHyphenAccountDetails(hyphenAccountDetailsJson);
            account.setUpdatedAt(java.time.LocalDateTime.now());
            account.setLastSyncedAt(LocalDateTime.now());
            brokerAccountRepository.save(account);

            // 저장 후 최신 계좌 정보 조회해서 반환 (일관성 있는 응답)
            return brokerAccountService.getAccount(account.getUserId(), accountId);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update account details for accountId={}", accountId, e);
            throw ApiException.internalServerError("계좌 정보 업데이트 실패", "ACCOUNT_DETAILS_UPDATE_ERROR");
        }
    }
}
