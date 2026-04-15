package com.project.server.service.auth;

import com.project.server.domain.UserEntity;
import com.project.server.domain.UserProfileEntity;
import com.project.server.domain.UserWatchAssetEntity;
import com.project.server.dto.AuthDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.UserProfileRepository;
import com.project.server.repository.UserWatchAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserJpaRepository userJpaRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserWatchAssetRepository userWatchAssetRepository;
    private final WatchAssetSelectionService watchAssetSelectionService;

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedNickname = request.getNickname().trim();

        // 중복 이메일 체크
        if (userJpaRepository.findByEmail(normalizedEmail).isPresent()) {
            throw ApiException.conflict("이미 존재하는 이메일입니다.", "AUTH_EMAIL_DUPLICATED");
        }

        UserEntity newUser = UserEntity.builder()
            .email(normalizedEmail)
            .nickname(normalizedNickname)
                .password(request.getPassword()) // 실제로는 암호화해야 함
                .build();

        UserEntity saved = userJpaRepository.save(newUser);

        log.info("새 사용자 등록: {}", normalizedEmail);

        return AuthDto.AuthResponse.builder()
                .userId(saved.getId())
                .email(saved.getEmail())
                .nickname(saved.getNickname())
                .build();
    }

    public AuthDto.LoginResult login(AuthDto.LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        UserEntity user = userJpaRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null) {
            throw ApiException.notFound("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND");
        }

        if (!user.getPassword().equals(request.getPassword())) {
            throw ApiException.badRequest("비밀번호가 일치하지 않습니다.", "AUTH_INVALID_PASSWORD");
        }

        log.info("사용자 로그인: {}", normalizedEmail);

        return AuthDto.LoginResult.builder()
                .userId(user.getId())
            .email(user.getEmail())
                .nickname(user.getNickname())
                .accessToken(UUID.randomUUID().toString())
                .refreshToken(UUID.randomUUID().toString())
                .onboardingCompleted(false)
                .build();
    }

    public AuthDto.AuthResponse registerFCMToken(AuthDto.FCMTokenRequest request) {
        UserEntity user = userJpaRepository.findById(request.getUserId()).orElse(null);

        if (user == null) {
            throw ApiException.badRequest("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND");
        }

        // FCM 토큰 업데이트
        user.setFcmToken(request.getFcmToken());
        userJpaRepository.save(user);

        String token = request.getFcmToken();
        String tokenPreview = token.substring(0, Math.min(50, token.length()));
        log.info("FCM 토큰 등록: 사용자={}, 토큰={}...", user.getEmail(), tokenPreview);

        return AuthDto.AuthResponse.builder()
                .userId(user.getId())
            .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }

    public AuthDto.AuthResponse deleteAccount(Long userId) {
        UserEntity user = userJpaRepository.findById(userId).orElse(null);

        if (user == null) {
            throw ApiException.badRequest("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND");
        }

        userJpaRepository.deleteById(userId);
    log.info("사용자 탈퇴: {}", user.getEmail());

        return AuthDto.AuthResponse.builder()
                .userId(userId)
        .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }

    public AuthDto.AuthResponse updateNickname(Long userId, String nickname) {
        UserEntity user = userJpaRepository.findById(userId).orElse(null);
        if (user == null) {
            throw ApiException.badRequest("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND");
        }

        user.setNickname(nickname);
        userJpaRepository.save(user);

        return AuthDto.AuthResponse.builder()
                .userId(user.getId())
            .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }

    public List<AuthDto.AccountInfo> getAllAccounts() {
        List<UserEntity> users = userJpaRepository.findAll();
        return users.stream()
                .map(user -> AuthDto.AccountInfo.builder()
                        .userId(user.getId())
                .email(user.getEmail())
                        .nickname(user.getNickname())
                        .build())
                .collect(Collectors.toList());
    }

    public AuthDto.MeResponse getMe(Long userId) {
        UserEntity user = userJpaRepository.findById(userId).orElse(null);
        UserProfileEntity profile = userProfileRepository.findByUserId(userId).orElse(null);
        List<UserWatchAssetEntity> watchAssets = userWatchAssetRepository.findByUserIdOrderByDisplayOrderAsc(userId);

        String displayName = (user != null && user.getNickname() != null && !user.getNickname().isBlank())
                ? user.getNickname()
            : (user != null ? user.getEmail() : "사용자");

        String avatarText = profile == null ? "JY" : profile.getAvatarText();
        int weeklyLearningCount = profile == null ? 6 : profile.getWeeklyLearningCount();
        int quizAccuracyPercent = profile == null ? 82 : profile.getQuizAccuracyPercent();
        String weakTopic = profile == null ? "환율" : profile.getWeakTopic();

        List<AuthDto.WatchAssetReturn> watchAssetReturns = watchAssets.isEmpty()
            ? watchAssetSelectionService.getSelectedAssets(userId).stream()
            .map(asset -> AuthDto.WatchAssetReturn.builder()
                .assetName(asset.getAssetName())
                .changePercent(asset.getChangePercent())
                .build())
            .toList()
            : watchAssets.stream()
            .map(asset -> AuthDto.WatchAssetReturn.builder()
                .assetName(asset.getAssetName())
                .changePercent(asset.getChangePercent())
                .build())
            .toList();

        return AuthDto.MeResponse.builder()
                .profile(AuthDto.Profile.builder()
                        .avatarText(avatarText)
                        .name(displayName)
                        .summaryText("이번 주 학습 " + weeklyLearningCount + "회 · 퀴즈 정답률 " + quizAccuracyPercent + "%")
                        .build())
                .watchAssets(watchAssetReturns)
                .watchAssetsLinkText("추적 중 " + watchAssetReturns.size() + "개 자산")
                .studyStats(List.of(
                        AuthDto.StudyStat.builder().label("이번 주 학습").valueText(weeklyLearningCount + "회").build(),
                        AuthDto.StudyStat.builder().label("퀴즈 정답률").valueText(quizAccuracyPercent + "%").build(),
                        AuthDto.StudyStat.builder().label("가장 약한 단원").valueText(weakTopic).build()
                ))
                .settingsMenu(List.of(
                        AuthDto.SettingMenuItem.builder().key("notification").title("알림 설정").description("발표 직전, 브리핑 거점, 학습 퀴즈").build(),
                        AuthDto.SettingMenuItem.builder().key("simple_explain").title("쉬운 설명 기본값").description("켜짐").build(),
                        AuthDto.SettingMenuItem.builder().key("color_mode").title("색상 모드").description("한국식 (빨강=상승)").build(),
                        AuthDto.SettingMenuItem.builder().key("market_pref").title("시장/국가 선호").description("미국, 한국").build(),
                        AuthDto.SettingMenuItem.builder().key("transparency").title("데이터 출처/모델 투명성").description("출처, 모델 버전 확인").build(),
                        AuthDto.SettingMenuItem.builder().key("account").title("계정 설정").description("보안, 닉네임, 로그아웃").build()
                ))
                .build();
    }

    /**
     * 비밀번호 변경 (사용자가 자신의 비밀번호 변경)
     */
    public AuthDto.AuthResponse changePassword(Long userId, String currentPassword, String newPassword) {
        UserEntity user = userJpaRepository.findById(userId).orElse(null);

        if (user == null) {
            throw ApiException.badRequest("존재하지 않는 사용자입니다.", "AUTH_USER_NOT_FOUND");
        }

        // 현재 비밀번호 검증
        if (!user.getPassword().equals(currentPassword)) {
            throw ApiException.badRequest("현재 비밀번호가 일치하지 않습니다.", "AUTH_INVALID_PASSWORD");
        }

        user.setPassword(newPassword);
        userJpaRepository.save(user);
        log.info("사용자 비밀번호 변경: {}", user.getEmail());

        return AuthDto.AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }
}