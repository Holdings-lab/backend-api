package com.project.server.service.asset;

import com.project.server.domain.asset.FinancialGoal;
import com.project.server.domain.asset.ScheduleStatus;
import com.project.server.domain.asset.InvestmentHorizon;
import com.project.server.domain.asset.UserInvestmentGoalEntity;
import com.project.server.domain.asset.UserInvestmentProfileEntity;
import com.project.server.dto.UserAssetDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.asset.UserInvestmentGoalRepository;
import com.project.server.repository.asset.UserInvestmentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class GoalService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int JUST_STARTED_DAYS = 30;

    private final UserInvestmentGoalRepository goalRepository;
    private final UserInvestmentProfileRepository profileRepository;
    private final AssetMetricsService assetMetricsService;

    public UserAssetDto.GoalResponse updateGoal(Long userId, UserAssetDto.UpdateGoalRequest request) {
        validateUserId(userId);
        if (request.getFinancialGoal() == null || request.getFinancialGoal().isBlank()) {
            throw ApiException.badRequest("financialGoal은 필수입니다.", "GOAL_REQUIRED");
        }
        if (request.getTargetAmount() == null || request.getTargetAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("targetAmount는 0보다 커야 합니다.", "INVALID_TARGET_AMOUNT");
        }

        FinancialGoal financialGoal;
        try {
            financialGoal = FinancialGoal.fromString(request.getFinancialGoal());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("유효하지 않은 financialGoal입니다.", "INVALID_FINANCIAL_GOAL");
        }
        BigDecimal currentAmount = assetMetricsService.getAssetTotal(userId);
        LocalDate today = LocalDate.now(KST);

        UserInvestmentGoalEntity goal = goalRepository.findById(userId)
                .orElse(UserInvestmentGoalEntity.builder().userId(userId).build());

        goal.setFinancialGoal(financialGoal);
        goal.setGoalLabel(financialGoal.label());
        goal.setTargetAmount(request.getTargetAmount());
        goal.setGoalStartAmount(currentAmount);
        goal.setGoalStartDate(today);
        goalRepository.save(goal);

        return UserAssetDto.GoalResponse.builder()
                .goalLabel(goal.getGoalLabel())
                .targetAmount(goal.getTargetAmount())
                .goalStartAmount(goal.getGoalStartAmount())
                .goalStartDate(goal.getGoalStartDate())
                .updatedAt(ZonedDateTime.now(KST).toOffsetDateTime())
                .build();
    }

    @Transactional(readOnly = true)
    public UserAssetDto.GoalProgressResponse getGoalProgress(Long userId) {
        validateUserId(userId);

        return goalRepository.findById(userId)
                .map(goal -> buildProgress(userId, goal))
                .orElse(UserAssetDto.GoalProgressResponse.builder()
                        .goalLabel(null)
                        .progressPct(null)
                        .scheduleStatus(null)
                        .scheduleNote(null)
                        .build());
    }

    private UserAssetDto.GoalProgressResponse buildProgress(Long userId, UserInvestmentGoalEntity goal) {
        BigDecimal currentAmount = assetMetricsService.getAssetTotal(userId);
        int progressPct = goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                ? currentAmount.divide(goal.getTargetAmount(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue()
                : 0;

        LocalDate today = LocalDate.now(KST);
        long elapsedDays = ChronoUnit.DAYS.between(goal.getGoalStartDate(), today);

        if (elapsedDays < JUST_STARTED_DAYS) {
            return UserAssetDto.GoalProgressResponse.builder()
                    .goalLabel(goal.getGoalLabel())
                    .progressPct(progressPct)
                    .scheduleStatus(ScheduleStatus.JUST_STARTED.name())
                    .scheduleNote("30일 후부터 속도를 알려드려요")
                    .build();
        }

        InvestmentHorizon horizon = profileRepository.findById(userId)
                .map(UserInvestmentProfileEntity::getInvestmentHorizon)
                .orElse(InvestmentHorizon.Y1_3);

        BigDecimal totalNeeded = goal.getTargetAmount().subtract(goal.getGoalStartAmount());
        if (totalNeeded.compareTo(BigDecimal.ZERO) <= 0) {
            return UserAssetDto.GoalProgressResponse.builder()
                    .goalLabel(goal.getGoalLabel())
                    .progressPct(progressPct)
                    .scheduleStatus(ScheduleStatus.ON_TRACK.name())
                    .scheduleNote("계획대로 진행 중이에요")
                    .build();
        }

        double elapsedMonths = elapsedDays / 30.0;
        double horizonMonths = horizon.representativeMonths();
        BigDecimal expectedAmount = goal.getGoalStartAmount().add(
                totalNeeded.multiply(BigDecimal.valueOf(elapsedMonths / horizonMonths)));

        BigDecimal monthlyPace = totalNeeded.divide(
                BigDecimal.valueOf(horizonMonths), 6, RoundingMode.HALF_UP);
        BigDecimal delta = currentAmount.subtract(expectedAmount);

        if (monthlyPace.compareTo(BigDecimal.ZERO) <= 0) {
            return UserAssetDto.GoalProgressResponse.builder()
                    .goalLabel(goal.getGoalLabel())
                    .progressPct(progressPct)
                    .scheduleStatus(ScheduleStatus.ON_TRACK.name())
                    .scheduleNote("계획대로 진행 중이에요")
                    .build();
        }

        int monthsDiff = delta.divide(monthlyPace, 0, RoundingMode.HALF_UP).intValue();

        if (monthsDiff >= 1) {
            return UserAssetDto.GoalProgressResponse.builder()
                    .goalLabel(goal.getGoalLabel())
                    .progressPct(progressPct)
                    .scheduleStatus(ScheduleStatus.AHEAD.name())
                    .scheduleNote("예정보다 " + monthsDiff + "개월 빠름")
                    .build();
        }
        if (monthsDiff <= -1) {
            return UserAssetDto.GoalProgressResponse.builder()
                    .goalLabel(goal.getGoalLabel())
                    .progressPct(progressPct)
                    .scheduleStatus(ScheduleStatus.BEHIND.name())
                    .scheduleNote("예정보다 " + Math.abs(monthsDiff) + "개월 느림")
                    .build();
        }

        return UserAssetDto.GoalProgressResponse.builder()
                .goalLabel(goal.getGoalLabel())
                .progressPct(progressPct)
                .scheduleStatus(ScheduleStatus.ON_TRACK.name())
                .scheduleNote("계획대로 진행 중이에요")
                .build();
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
    }
}
