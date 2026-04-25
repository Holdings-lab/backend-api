package com.project.server.service.auth;

import com.project.server.domain.EmailVerificationCodeEntity;
import com.project.server.domain.UserEntity;
import com.project.server.domain.UserProfileEntity;
import com.project.server.domain.UserWatchAssetEntity;
import com.project.server.dto.AuthDto;
import com.project.server.exception.ApiException;
import com.project.server.repository.EmailVerificationCodeRepository;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.UserProfileRepository;
import com.project.server.repository.UserWatchAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

        private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserJpaRepository userJpaRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserWatchAssetRepository userWatchAssetRepository;
        private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final WatchAssetSelectionService watchAssetSelectionService;
        private final PasswordEncoder passwordEncoder;
        private final JavaMailSender mailSender;

        @Value("${auth.email.from}")
        private String authMailFrom;

        @Value("${auth.email.verify-code-expire-minutes:10}")
        private long verifyCodeExpireMinutes;

        public AuthDto.EmailVerificationResponse sendEmailVerificationCode(String email) {
                String normalizedEmail = email.trim().toLowerCase();

                if (userJpaRepository.findByEmail(normalizedEmail).isPresent()) {
                        throw ApiException.conflict("이미 존재하는 이메일입니다.", "AUTH_EMAIL_DUPLICATED");
                }

                String verificationCode = generateVerificationCode();
                LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(verifyCodeExpireMinutes);

                emailVerificationCodeRepository.deleteByEmail(normalizedEmail);
                emailVerificationCodeRepository.save(EmailVerificationCodeEntity.builder()
                                .email(normalizedEmail)
                                .verificationCode(verificationCode)
                                .expiresAt(expiresAt)
                                .build());

                sendVerificationEmail(normalizedEmail, verificationCode);

                log.info("이메일 인증코드 발송: email={}", normalizedEmail);

                return AuthDto.EmailVerificationResponse.builder()
                                .email(normalizedEmail)
                                .verified(false)
                                .message("인증번호를 이메일로 전송했습니다.")
                                .build();
        }

        public AuthDto.EmailVerificationResponse verifyEmailCode(String email, String verificationCode) {
                String normalizedEmail = email.trim().toLowerCase();
                validateAndMarkVerified(normalizedEmail, verificationCode);

                return AuthDto.EmailVerificationResponse.builder()
                                .email(normalizedEmail)
                                .verified(true)
                                .message("이메일 인증이 완료되었습니다.")
                                .build();
        }

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        String normalizedNickname = request.getNickname().trim();

        // 중복 이메일 체크
        if (userJpaRepository.findByEmail(normalizedEmail).isPresent()) {
            throw ApiException.conflict("이미 존재하는 이메일입니다.", "AUTH_EMAIL_DUPLICATED");
        }

        ensureEmailVerifiedForRegistration(normalizedEmail);

        UserEntity newUser = UserEntity.builder()
                .email(normalizedEmail)
                .nickname(normalizedNickname)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        UserEntity saved = userJpaRepository.save(newUser);
        emailVerificationCodeRepository.deleteByEmail(normalizedEmail);

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

                boolean passwordMatched = passwordEncoder.matches(request.getPassword(), user.getPassword());
                if (!passwordMatched
                                && !isBcryptHash(user.getPassword())
                                && user.getPassword().equals(request.getPassword())) {
                        user.setPassword(passwordEncoder.encode(request.getPassword()));
                        userJpaRepository.save(user);
                        passwordMatched = true;
                        log.info("기존 평문 비밀번호를 해시로 업그레이드: {}", normalizedEmail);
                }

                if (!passwordMatched) {
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
                        AuthDto.StudyStat.builder().label("가장 약한 단원").valueText(weakTopic).build()))
                .settingsMenu(List.of(
                        AuthDto.SettingMenuItem.builder().key("notification").title("알림 설정")
                                .description("발표 직전, 브리핑 거점, 학습 퀴즈").build(),
                        AuthDto.SettingMenuItem.builder().key("simple_explain").title("쉬운 설명 기본값").description("켜짐")
                                .build(),
                        AuthDto.SettingMenuItem.builder().key("color_mode").title("색상 모드").description("한국식 (빨강=상승)")
                                .build(),
                        AuthDto.SettingMenuItem.builder().key("market_pref").title("시장/국가 선호").description("미국, 한국")
                                .build(),
                        AuthDto.SettingMenuItem.builder().key("transparency").title("데이터 출처/모델 투명성")
                                .description("출처, 모델 버전 확인").build(),
                        AuthDto.SettingMenuItem.builder().key("account").title("계정 설정").description("보안, 닉네임, 로그아웃")
                                .build()))
                .build();
    }

        public AuthDto.Profile getMeProfile(Long userId) {
                return getMe(userId).getProfile();
        }

        public List<AuthDto.WatchAssetReturn> getMeWatchAssets(Long userId) {
                return getMe(userId).getWatchAssets();
        }

        public List<AuthDto.StudyStat> getMeStudyStats(Long userId) {
                return getMe(userId).getStudyStats();
        }

        public List<AuthDto.SettingMenuItem> getMeSettingsMenu(Long userId) {
                return getMe(userId).getSettingsMenu();
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
                boolean currentPasswordMatched = passwordEncoder.matches(currentPassword, user.getPassword());
                if (!currentPasswordMatched
                                && !isBcryptHash(user.getPassword())
                                && user.getPassword().equals(currentPassword)) {
                        currentPasswordMatched = true;
                }

                if (!currentPasswordMatched) {
            throw ApiException.badRequest("현재 비밀번호가 일치하지 않습니다.", "AUTH_INVALID_PASSWORD");
        }

                user.setPassword(passwordEncoder.encode(newPassword));
        userJpaRepository.save(user);
        log.info("사용자 비밀번호 변경: {}", user.getEmail());

        return AuthDto.AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .build();
    }

    /**
     * OAuth 로그인 (Apple, Google, 카카오)
     */
    public AuthDto.OAuthLoginResult oauthLogin(AuthDto.OAuthLoginRequest request) {
        String provider = request.getProvider().toLowerCase();

        // OAuth 제공자 검증
        if (!provider.matches("apple|google|kakao")) {
            throw ApiException.badRequest("지원하지 않는 OAuth 제공자입니다.", "AUTH_INVALID_OAUTH_PROVIDER");
        }

        // 실제로는 OAuth 제공자에서 토큰을 검증하고 사용자 정보를 받아야 함
        // 현재는 시뮬레이션: authorizationCode로 OAuth 제공자와 통신
        String oauthUserId = generateOAuthUserId(provider, request.getAuthorizationCode());
        String oauthEmail = generateOAuthEmail(provider, oauthUserId);
        String oauthNickname = generateOAuthNickname(provider, oauthUserId);

        // 기존 사용자 조회 (OAuth 제공자 + ID 조합으로)
        UserEntity user = userJpaRepository.findByEmail(oauthEmail).orElse(null);

        boolean isNewUser = (user == null);

        if (isNewUser) {
            // 새로운 사용자 생성
            user = UserEntity.builder()
                    .email(oauthEmail)
                    .nickname(oauthNickname)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .oauthProvider(provider)
                    .oauthId(oauthUserId)
                    .build();
            user = userJpaRepository.save(user);
            log.info("새 OAuth 사용자 등록: provider={}, email={}", provider, oauthEmail);
        } else {
            // 기존 사용자 - OAuth 정보 업데이트
            user.setOauthProvider(provider);
            user.setOauthId(oauthUserId);
            userJpaRepository.save(user);
            log.info("OAuth 사용자 로그인: provider={}, email={}", provider, oauthEmail);
        }

        return AuthDto.OAuthLoginResult.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .accessToken(UUID.randomUUID().toString())
                .refreshToken(UUID.randomUUID().toString())
                .onboardingCompleted(false)
                .newUser(isNewUser)
                .build();
    }

    // OAuth 시뮬레이션 헬퍼 메서드 (실제로는 외부 OAuth 제공자와 통신)
    private String generateOAuthUserId(String provider, String authorizationCode) {
        return provider + "_" + UUID.randomUUID().toString().substring(0, 12);
    }

    private String generateOAuthEmail(String provider, String oauthUserId) {
        return oauthUserId + "@" + provider + "-oauth.com";
    }

    private String generateOAuthNickname(String provider, String oauthUserId) {
        return provider.toUpperCase() + "_" + oauthUserId.substring(0, 8);
    }

        private void sendVerificationEmail(String email, String code) {
                try {
                        SimpleMailMessage mailMessage = new SimpleMailMessage();
                        mailMessage.setFrom(authMailFrom);
                        mailMessage.setTo(email);
                        mailMessage.setSubject("[Holdings Lab] 이메일 인증번호 안내");
                        mailMessage.setText("Holdings Lab 이메일 인증 안내\n\n"
                                        + "인증번호: " + code + "\n"
                                        + "유효시간: " + verifyCodeExpireMinutes + "분\n\n"
                                        + "앱의 인증번호 입력란에 위 코드를 입력해주세요.\n"
                                        + "본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.");
                        mailSender.send(mailMessage);
                } catch (Exception ex) {
                        log.error("이메일 인증코드 발송 실패: email={}", email, ex);
                        throw ApiException.badRequest("이메일 발송에 실패했습니다. 메일 설정을 확인해주세요.", "AUTH_EMAIL_SEND_FAILED");
                }
        }

        private void validateAndMarkVerified(String normalizedEmail, String verificationCode) {
                EmailVerificationCodeEntity emailCode = emailVerificationCodeRepository.findTopByEmailOrderByIdDesc(normalizedEmail)
                                .orElseThrow(() -> ApiException.badRequest("이메일 인증요청이 필요합니다.", "AUTH_EMAIL_VERIFICATION_REQUIRED"));

                if (emailCode.getExpiresAt().isBefore(LocalDateTime.now())) {
                        throw ApiException.badRequest("인증번호가 만료되었습니다.", "AUTH_EMAIL_CODE_EXPIRED");
                }

                if (!emailCode.getVerificationCode().equals(verificationCode)) {
                        throw ApiException.badRequest("인증번호가 일치하지 않습니다.", "AUTH_EMAIL_CODE_INVALID");
                }

                emailCode.setVerifiedAt(LocalDateTime.now());
                emailVerificationCodeRepository.save(emailCode);
        }

        private void ensureEmailVerifiedForRegistration(String normalizedEmail) {
                EmailVerificationCodeEntity emailCode = emailVerificationCodeRepository.findTopByEmailOrderByIdDesc(normalizedEmail)
                                .orElseThrow(() -> ApiException.badRequest("이메일 인증이 필요합니다.", "AUTH_EMAIL_VERIFICATION_REQUIRED"));

                if (emailCode.getExpiresAt().isBefore(LocalDateTime.now())) {
                        throw ApiException.badRequest("이메일 인증이 만료되었습니다. 다시 인증해주세요.", "AUTH_EMAIL_CODE_EXPIRED");
                }

                if (emailCode.getVerifiedAt() == null) {
                        throw ApiException.badRequest("이메일 인증번호 확인이 필요합니다.", "AUTH_EMAIL_NOT_VERIFIED");
                }
        }

        private String generateVerificationCode() {
                int random = SECURE_RANDOM.nextInt(900000) + 100000;
                return Integer.toString(random);
        }

        private boolean isBcryptHash(String password) {
                return password != null
                                && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
        }
}