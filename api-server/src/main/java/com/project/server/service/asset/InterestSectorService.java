package com.project.server.service.asset;

import com.project.server.domain.asset.InterestSector;
import com.project.server.domain.asset.UserInvestmentProfileEntity;
import com.project.server.dto.UserPreferenceDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.asset.UserInvestmentProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class InterestSectorService {

    private final UserInvestmentProfileRepository profileRepository;

    @Transactional(readOnly = true)
    public UserPreferenceDto.InterestOptionsResponse getOptions() {
        return UserPreferenceDto.InterestOptionsResponse.builder()
                .options(getAllOptions())
                .build();
    }

    @Transactional(readOnly = true)
    public UserPreferenceDto.InterestsResponse getUserInterests(Long userId) {
        validateUserId(userId);
        Set<InterestSector> sectors = profileRepository.findById(userId)
                .map(UserInvestmentProfileEntity::getInterests)
                .orElseGet(LinkedHashSet::new);
        return toResponse(sectors);
    }

    public UserPreferenceDto.InterestsResponse updateUserInterests(
            Long userId,
            UserPreferenceDto.UpdateInterestsRequest request) {
        validateUserId(userId);
        if (request == null || request.getInterests() == null) {
            throw ApiException.badRequest("요청 본문이 필요합니다.", "INVALID_REQUEST");
        }

        UserInvestmentProfileEntity profile = profileRepository.findById(userId)
                .orElseGet(() -> UserInvestmentProfileEntity.builder().userId(userId).build());
        profile.setInterests(parseInterests(request.getInterests()));
        profileRepository.save(profile);
        return toResponse(profile.getInterests());
    }

    public Set<InterestSector> parseInterests(List<String> rawInterests) {
        if (rawInterests.isEmpty() || rawInterests.size() > 5) {
            throw ApiException.badRequest("interests는 1개 이상 5개 이하여야 합니다.", "INVALID_INTERESTS_SIZE");
        }

        Set<InterestSector> sectors = new LinkedHashSet<>();
        for (String raw : rawInterests) {
            if (raw == null || raw.isBlank()) {
                throw ApiException.badRequest("interests에 빈 값이 포함될 수 없습니다.", "INVALID_INTEREST");
            }
            try {
                sectors.add(InterestSector.fromString(raw.trim()));
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("유효하지 않은 interest입니다: " + raw, "INVALID_INTEREST");
            }
        }
        if (sectors.size() != rawInterests.size()) {
            throw ApiException.badRequest("interests에 중복 값이 있습니다.", "DUPLICATE_INTEREST");
        }
        return sectors;
    }

    private List<UserPreferenceDto.InterestItem> getAllOptions() {
        return Arrays.stream(InterestSector.values())
                .map(this::toItem)
                .collect(Collectors.toList());
    }

    private UserPreferenceDto.InterestsResponse toResponse(Set<InterestSector> sectors) {
        List<UserPreferenceDto.InterestItem> items = sectors == null
                ? List.of()
                : sectors.stream().map(this::toItem).collect(Collectors.toList());
        return UserPreferenceDto.InterestsResponse.builder()
                .interests(items)
                .count(items.size())
                .build();
    }

    private UserPreferenceDto.InterestItem toItem(InterestSector sector) {
        return UserPreferenceDto.InterestItem.builder()
                .code(sector.name())
                .label(sector.label())
                .build();
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw ApiException.badRequest("유효하지 않은 사용자 ID입니다.", "INVALID_USER_ID");
        }
    }
}
