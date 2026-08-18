package com.project.server.service.onboarding;

import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.asset.*;
import com.project.server.domain.broker.SupportedBroker;
import com.project.server.dto.OnboardingDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.asset.UserInvestmentGoalRepository;
import com.project.server.repository.asset.UserInvestmentProfileRepository;
import com.project.server.repository.asset.UserOnboardingProgressRepository;
import com.project.server.service.asset.AssetMetricsService;
import com.project.server.service.asset.InterestSectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OnboardingService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final BigDecimal MIN_TARGET_AMOUNT = BigDecimal.valueOf(10_000_000L);
    private static final BigDecimal MAX_TARGET_AMOUNT = BigDecimal.valueOf(300_000_000L);
    private static final int TOTAL_STEPS = 7;

    private final UserJpaRepository userJpaRepository;
    private final UserInvestmentGoalRepository goalRepository;
    private final UserInvestmentProfileRepository profileRepository;
    private final UserOnboardingProgressRepository progressRepository;
    private final BrokerAccountRepository brokerAccountRepository;
    private final AssetMetricsService assetMetricsService;
    private final InterestSectorService interestSectorService;

    public OnboardingDto.ProfileData updateProfile(Long userId, OnboardingDto.UpdateProfileRequest request) {
        validateUserId(userId);
        ensureUserExists(userId);
        if (request == null) {
            throw ApiException.badRequest("요청 본문이 필요합니다.", "EMPTY_REQUEST");
        }
        rejectBlankStrings(request);
        if (!hasAnyField(request)) {
            throw ApiException.badRequest("수정할 필드가 없습니다.", "EMPTY_PROFILE_PATCH");
        }

        if (request.getFinancialGoal() != null || request.getTargetAmount() != null) {
            upsertGoal(userId, request);
        }
        if (request.getInvestmentHorizon() != null
                || request.getRiskTolerance() != null
                || request.getInvestmentStyle() != null
                || request.getInterests() != null) {
            upsertProfile(userId, request);
        }

        refreshCompletion(userId);
        return buildSavedData(userId);
    }

    @Transactional(readOnly = true)
    public OnboardingDto.StatusResponse getStatus(Long userId) {
        validateUserId(userId);
        ensureUserExists(userId);

        boolean accountLinked = hasLinkedAccount(userId);
        boolean accountSkipped = progressRepository.findById(userId)
                .map(UserOnboardingProgressEntity::isAccountSkipped)
                .orElse(false);
        int lastCompletedStep = computeLastCompletedStep(userId, accountLinked, accountSkipped);

        return OnboardingDto.StatusResponse.builder()
                .lastCompletedStep(lastCompletedStep)
                .completed(lastCompletedStep >= TOTAL_STEPS)
                .accountLinked(accountLinked)
                .accountSkipped(accountSkipped)
                .savedData(buildSavedData(userId))
                .build();
    }

    public void skipAccountLink(Long userId) {
        validateUserId(userId);
        ensureUserExists(userId);

        if (computeProfileStep(userId) < 6) {
            throw ApiException.badRequest(
                    "투자 프로필(1~6단계)을 먼저 완료해주세요.",
                    "ONBOARDING_PROFILE_INCOMPLETE");
        }

        UserOnboardingProgressEntity progress = getOrCreateProgress(userId);
        progress.setAccountSkipped(true);
        progressRepository.save(progress);
        refreshCompletion(userId);
    }

    public void markAccountLinked(Long userId) {
        if (userId == null || userId <= 0 || !userJpaRepository.existsById(userId)) {
            return;
        }
        UserOnboardingProgressEntity progress = getOrCreateProgress(userId);
        if (progress.isAccountSkipped()) {
            progress.setAccountSkipped(false);
        }
        progressRepository.save(progress);
        refreshCompletion(userId);
    }

    @Transactional(readOnly = true)
    public boolean isOnboardingCompleted(Long userId) {
        if (userId == null || userId <= 0) {
            return false;
        }
        boolean accountLinked = hasLinkedAccount(userId);
        boolean accountSkipped = progressRepository.findById(userId)
                .map(UserOnboardingProgressEntity::isAccountSkipped)
                .orElse(false);
        return computeLastCompletedStep(userId, accountLinked, accountSkipped) >= TOTAL_STEPS;
    }

    @Transactional(readOnly = true)
    public OnboardingDto.BrokerListResponse listBrokers() {
        List<OnboardingDto.BrokerOption> brokers = Arrays.stream(SupportedBroker.values())
                .map(broker -> OnboardingDto.BrokerOption.builder()
                        .code(broker.code())
                        .name(broker.displayName())
                        .available(broker.available())
                        .build())
                .toList();
        return OnboardingDto.BrokerListResponse.builder().brokers(brokers).build();
    }

    private void upsertGoal(Long userId, OnboardingDto.UpdateProfileRequest request) {
        FinancialGoal incomingGoal = null;
        if (request.getFinancialGoal() != null) {
            try {
                incomingGoal = FinancialGoal.fromString(request.getFinancialGoal());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("유효하지 않은 financialGoal입니다.", "INVALID_FINANCIAL_GOAL");
            }
        }

        if (request.getTargetAmount() != null) {
            if (request.getTargetAmount().compareTo(MIN_TARGET_AMOUNT) < 0
                    || request.getTargetAmount().compareTo(MAX_TARGET_AMOUNT) > 0) {
                throw ApiException.badRequest(
                        "targetAmount는 1,000만원 이상 3억원 이하여야 합니다.",
                        "INVALID_TARGET_AMOUNT");
            }
        }

        var existingGoal = goalRepository.findById(userId);
        FinancialGoal resolvedGoal = incomingGoal != null
                ? incomingGoal
                : existingGoal.map(UserInvestmentGoalEntity::getFinancialGoal)
                        .orElseGet(() -> progressRepository.findById(userId)
                                .map(UserOnboardingProgressEntity::getDraftFinancialGoal)
                                .orElse(null));
        BigDecimal resolvedTarget = request.getTargetAmount() != null
                ? request.getTargetAmount()
                : existingGoal.map(UserInvestmentGoalEntity::getTargetAmount).orElse(null);

        // 1단계만: financialGoal draft 저장
        if (resolvedTarget == null) {
            if (resolvedGoal == null) {
                throw ApiException.badRequest("financialGoal이 필요합니다.", "GOAL_REQUIRED");
            }
            UserOnboardingProgressEntity progress = getOrCreateProgress(userId);
            progress.setDraftFinancialGoal(resolvedGoal);
            progressRepository.save(progress);
            return;
        }

        if (resolvedGoal == null) {
            throw ApiException.badRequest("financialGoal이 필요합니다.", "GOAL_REQUIRED");
        }

        UserInvestmentGoalEntity goal = existingGoal
                .orElseGet(() -> UserInvestmentGoalEntity.builder().userId(userId).build());
        goal.setFinancialGoal(resolvedGoal);
        goal.setGoalLabel(resolvedGoal.label());
        goal.setTargetAmount(resolvedTarget);
        if (goal.getGoalStartAmount() == null) {
            goal.setGoalStartAmount(assetMetricsService.getAssetTotal(userId));
        }
        if (goal.getGoalStartDate() == null) {
            goal.setGoalStartDate(LocalDate.now(KST));
        }
        goalRepository.save(goal);

        progressRepository.findById(userId).ifPresent(progress -> {
            progress.setDraftFinancialGoal(null);
            progressRepository.save(progress);
        });
    }

    private void upsertProfile(Long userId, OnboardingDto.UpdateProfileRequest request) {
        UserInvestmentProfileEntity profile = profileRepository.findById(userId)
                .orElseGet(() -> UserInvestmentProfileEntity.builder().userId(userId).build());

        if (request.getInvestmentHorizon() != null) {
            try {
                profile.setInvestmentHorizon(InvestmentHorizon.fromString(request.getInvestmentHorizon()));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("유효하지 않은 investmentHorizon입니다.", "INVALID_INVESTMENT_HORIZON");
            }
        }

        if (request.getRiskTolerance() != null) {
            try {
                RiskToleranceBand band = RiskToleranceBand.fromString(request.getRiskTolerance());
                profile.setMaxDrawdownTolerance(band.percent());
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("유효하지 않은 riskTolerance입니다.", "INVALID_RISK_TOLERANCE");
            }
        }

        if (request.getInvestmentStyle() != null) {
            try {
                profile.setInvestmentStyle(InvestmentStyle.fromString(request.getInvestmentStyle()));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("유효하지 않은 investmentStyle입니다.", "INVALID_INVESTMENT_STYLE");
            }
        }

        if (request.getInterests() != null) {
            profile.setInterests(interestSectorService.parseInterests(request.getInterests()));
        }

        profileRepository.save(profile);
    }

    private OnboardingDto.ProfileData buildSavedData(Long userId) {
        var goalOpt = goalRepository.findById(userId);
        var profileOpt = profileRepository.findById(userId);
        FinancialGoal draftGoal = progressRepository.findById(userId)
                .map(UserOnboardingProgressEntity::getDraftFinancialGoal)
                .orElse(null);

        String financialGoal = goalOpt.map(UserInvestmentGoalEntity::getFinancialGoal)
                .map(Enum::name)
                .orElse(draftGoal != null ? draftGoal.name() : null);

        return OnboardingDto.ProfileData.builder()
                .financialGoal(financialGoal)
                .targetAmount(goalOpt.map(UserInvestmentGoalEntity::getTargetAmount).orElse(null))
                .investmentHorizon(profileOpt.map(UserInvestmentProfileEntity::getInvestmentHorizon)
                        .map(Enum::name).orElse(null))
                .riskTolerance(profileOpt.map(UserInvestmentProfileEntity::getMaxDrawdownTolerance)
                        .map(RiskToleranceBand::fromPercent)
                        .map(band -> band != null ? band.name() : null)
                        .orElse(null))
                .investmentStyle(profileOpt.map(UserInvestmentProfileEntity::getInvestmentStyle)
                        .map(Enum::name).orElse(null))
                .interests(profileOpt.map(UserInvestmentProfileEntity::getInterests)
                        .filter(set -> set != null && !set.isEmpty())
                        .map(set -> set.stream().map(Enum::name).collect(Collectors.toList()))
                        .orElse(null))
                .build();
    }

    private int computeProfileStep(Long userId) {
        OnboardingDto.ProfileData data = buildSavedData(userId);
        if (data.getFinancialGoal() == null) {
            return 0;
        }
        if (data.getTargetAmount() == null) {
            return 1;
        }
        if (data.getInvestmentHorizon() == null) {
            return 2;
        }
        if (data.getRiskTolerance() == null) {
            return 3;
        }
        if (data.getInvestmentStyle() == null) {
            return 4;
        }
        if (data.getInterests() == null || data.getInterests().isEmpty()) {
            return 5;
        }
        return 6;
    }

    private int computeLastCompletedStep(Long userId, boolean accountLinked, boolean accountSkipped) {
        int profileStep = computeProfileStep(userId);
        if (profileStep < 6) {
            return profileStep;
        }
        if (accountLinked || accountSkipped) {
            return TOTAL_STEPS;
        }
        return 6;
    }

    private void refreshCompletion(Long userId) {
        boolean accountLinked = hasLinkedAccount(userId);
        UserOnboardingProgressEntity progress = getOrCreateProgress(userId);
        int step = computeLastCompletedStep(userId, accountLinked, progress.isAccountSkipped());
        if (step >= TOTAL_STEPS) {
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(LocalDateTime.now());
            }
        } else {
            progress.setCompletedAt(null);
        }
        progressRepository.save(progress);
    }

    private boolean hasLinkedAccount(Long userId) {
        return brokerAccountRepository.findByUserId(userId).stream()
                .anyMatch(account -> account.getHyphenStatus() == BrokerAccountEntity.HyphenStatus.CONNECTED);
    }

    private UserOnboardingProgressEntity getOrCreateProgress(Long userId) {
        return progressRepository.findById(userId)
                .orElseGet(() -> UserOnboardingProgressEntity.builder()
                        .userId(userId)
                        .accountSkipped(false)
                        .build());
    }

    private void rejectBlankStrings(OnboardingDto.UpdateProfileRequest request) {
        if (request.getFinancialGoal() != null && request.getFinancialGoal().isBlank()) {
            throw ApiException.badRequest("financialGoal은 비울 수 없습니다.", "NULL_NOT_ALLOWED");
        }
        if (request.getInvestmentHorizon() != null && request.getInvestmentHorizon().isBlank()) {
            throw ApiException.badRequest("investmentHorizon은 비울 수 없습니다.", "NULL_NOT_ALLOWED");
        }
        if (request.getRiskTolerance() != null && request.getRiskTolerance().isBlank()) {
            throw ApiException.badRequest("riskTolerance는 비울 수 없습니다.", "NULL_NOT_ALLOWED");
        }
        if (request.getInvestmentStyle() != null && request.getInvestmentStyle().isBlank()) {
            throw ApiException.badRequest("investmentStyle은 비울 수 없습니다.", "NULL_NOT_ALLOWED");
        }
    }

    private boolean hasAnyField(OnboardingDto.UpdateProfileRequest request) {
        return request.getFinancialGoal() != null
                || request.getTargetAmount() != null
                || request.getInvestmentHorizon() != null
                || request.getRiskTolerance() != null
                || request.getInvestmentStyle() != null
                || request.getInterests() != null;
    }

    private void ensureUserExists(Long userId) {
        if (!userJpaRepository.existsById(userId)) {
            throw ApiException.notFound("사용자를 찾을 수 없습니다.", "USER_NOT_FOUND");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
    }
}
