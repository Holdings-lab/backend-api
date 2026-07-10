package com.project.server.service.asset;

import com.project.server.domain.asset.InvestmentHorizon;
import com.project.server.domain.asset.UserInvestmentProfileEntity;
import com.project.server.dto.UserAssetDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.asset.UserInvestmentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InvestmentProfileService {

    private final UserInvestmentProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public UserAssetDto.InvestmentProfileResponse getProfile(Long userId) {
        validateUserId(userId);
        UserInvestmentProfileEntity profile = getOrCreate(userId);
        return toResponse(profile);
    }

    public UserAssetDto.InvestmentProfileResponse updateProfile(
            Long userId, UserAssetDto.UpdateInvestmentProfileRequest request) {
        validateUserId(userId);

        UserInvestmentProfileEntity profile = getOrCreate(userId);
        if (request.getInvestmentHorizon() != null && !request.getInvestmentHorizon().isBlank()) {
            try {
                profile.setInvestmentHorizon(InvestmentHorizon.valueOf(request.getInvestmentHorizon()));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("유효하지 않은 investmentHorizon입니다.", "INVALID_INVESTMENT_HORIZON");
            }
        }
        if (request.getMaxDrawdownTolerance() != null) {
            if (request.getMaxDrawdownTolerance() <= 0) {
                throw ApiException.badRequest("maxDrawdownTolerance는 0보다 커야 합니다.", "INVALID_TOLERANCE");
            }
            profile.setMaxDrawdownTolerance(request.getMaxDrawdownTolerance());
        }
        profileRepository.save(profile);
        return toResponse(profile);
    }

    private UserInvestmentProfileEntity getOrCreate(Long userId) {
        return profileRepository.findById(userId)
                .orElseGet(() -> {
                    UserInvestmentProfileEntity created = UserInvestmentProfileEntity.builder()
                            .userId(userId)
                            .investmentHorizon(InvestmentHorizon.ONE_TO_THREE_YEARS)
                            .maxDrawdownTolerance(10)
                            .build();
                    return profileRepository.save(created);
                });
    }

    private UserAssetDto.InvestmentProfileResponse toResponse(UserInvestmentProfileEntity profile) {
        return UserAssetDto.InvestmentProfileResponse.builder()
                .investmentHorizon(profile.getInvestmentHorizon().name())
                .maxDrawdownTolerance(profile.getMaxDrawdownTolerance())
                .build();
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
    }
}
