package com.project.server.config;

import com.project.server.domain.PolicyEventEntity;
import com.project.server.domain.UserEntity;
import com.project.server.domain.UserNotificationSettingEntity;
import com.project.server.domain.UserProfileEntity;
import com.project.server.domain.UserWatchAssetEntity;
import com.project.server.domain.WatchAssetCatalogEntity;
import com.project.server.repository.PolicyEventJpaRepository;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.UserNotificationSettingRepository;
import com.project.server.repository.UserProfileRepository;
import com.project.server.repository.UserWatchAssetRepository;
import com.project.server.repository.WatchAssetCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DatabaseSeedInitializer implements CommandLineRunner {

    private final UserJpaRepository userJpaRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserNotificationSettingRepository userNotificationSettingRepository;
    private final UserWatchAssetRepository userWatchAssetRepository;
    private final WatchAssetCatalogRepository watchAssetCatalogRepository;
    private final PolicyEventJpaRepository policyEventJpaRepository;

    @Override
    @Transactional
    public void run(String... args) {
        seedWatchAssetCatalog();
        seedUsers();
        seedProfiles();
        seedNotificationSettings();
        seedWatchAssets();
        seedPolicyEvents();
    }

    private void seedWatchAssetCatalog() {
        if (watchAssetCatalogRepository.count() > 0) {
            return;
        }

        watchAssetCatalogRepository.saveAll(List.of(
                WatchAssetCatalogEntity.builder().assetName("장기채 ETF").defaultChangePercent(-1.2).displayOrder(1).build(),
                WatchAssetCatalogEntity.builder().assetName("나스닥 성장주 ETF").defaultChangePercent(-0.8).displayOrder(2).build(),
                WatchAssetCatalogEntity.builder().assetName("달러 인덱스 ETF").defaultChangePercent(0.6).displayOrder(3).build(),
                WatchAssetCatalogEntity.builder().assetName("금 ETF").defaultChangePercent(1.5).displayOrder(4).build(),
                WatchAssetCatalogEntity.builder().assetName("비트코인 ETF").defaultChangePercent(3.2).displayOrder(5).build(),
                WatchAssetCatalogEntity.builder().assetName("코스피 ETF").defaultChangePercent(-0.5).displayOrder(6).build()
        ));
    }

    private void seedUsers() {
        if (userJpaRepository.count() > 0) {
            return;
        }

        userJpaRepository.saveAll(List.of(
                UserEntity.builder().email("jiyoung@example.com").nickname("지웅").password("password123").build(),
                UserEntity.builder().email("demo_user@example.com").nickname("데모").password("demo1234").build()
        ));
    }

    private void seedProfiles() {
        if (userProfileRepository.count() > 0) {
            return;
        }

        userProfileRepository.saveAll(List.of(
                UserProfileEntity.builder().userId(1L).avatarText("JY").weeklyLearningCount(6).quizAccuracyPercent(82).weakTopic("환율").build(),
                UserProfileEntity.builder().userId(2L).avatarText("DM").weeklyLearningCount(3).quizAccuracyPercent(71).weakTopic("고용").build()
        ));
    }

    private void seedNotificationSettings() {
        if (userNotificationSettingRepository.count() > 0) {
            return;
        }

        userNotificationSettingRepository.saveAll(List.of(
                UserNotificationSettingEntity.builder().userId(1L).before30m(true).importantEventBriefing(false).learningReminder(true).build(),
                UserNotificationSettingEntity.builder().userId(2L).before30m(true).importantEventBriefing(false).learningReminder(true).build()
        ));
    }

    private void seedWatchAssets() {
        if (userWatchAssetRepository.count() > 0) {
            return;
        }

        userWatchAssetRepository.saveAll(List.of(
                UserWatchAssetEntity.builder().userId(1L).assetName("장기채 ETF").changePercent(-1.2).signalText("하락확률 70%").displayOrder(1).build(),
                UserWatchAssetEntity.builder().userId(1L).assetName("나스닥 성장주 ETF").changePercent(-0.8).signalText("하락확률 70%").displayOrder(2).build(),
                UserWatchAssetEntity.builder().userId(1L).assetName("달러 인덱스 ETF").changePercent(0.6).signalText("상승확률 60%").displayOrder(3).build(),
                UserWatchAssetEntity.builder().userId(2L).assetName("장기채 ETF").changePercent(-0.3).signalText("하락확률 58%").displayOrder(1).build(),
                UserWatchAssetEntity.builder().userId(2L).assetName("코스피 ETF").changePercent(-0.5).signalText("하락확률 58%").displayOrder(2).build(),
                UserWatchAssetEntity.builder().userId(2L).assetName("금 ETF").changePercent(1.5).signalText("상승확률 72%").displayOrder(3).build()
        ));
    }

    private void seedPolicyEvents() {
        if (policyEventJpaRepository.count() > 0) {
            return;
        }

        policyEventJpaRepository.saveAll(List.of(
                PolicyEventEntity.builder()
                        .title("한국은행 기준금리 0.25%p 인상")
                        .keyword("금리")
                        .impactScore(78.0)
                        .analysisSummary("대출 이자 부담 증가와 성장주 밸류에이션 조정 가능성이 확대됩니다.")
                        .createdAt(LocalDateTime.now().minusHours(6))
                        .build(),
                PolicyEventEntity.builder()
                        .title("미국 비농업고용 발표")
                        .keyword("고용")
                        .impactScore(71.0)
                        .analysisSummary("고용 지표 강세 시 금리 인하 기대가 후퇴할 수 있어 변동성 확대 우려가 있습니다.")
                        .createdAt(LocalDateTime.now().minusHours(4))
                        .build(),
                PolicyEventEntity.builder()
                        .title("연준 위원 정책 발언")
                        .keyword("정책연설")
                        .impactScore(63.0)
                        .analysisSummary("발언 톤 변화가 달러 및 장기채 방향성에 단기적으로 영향을 줄 수 있습니다.")
                        .createdAt(LocalDateTime.now().minusHours(2))
                        .build()
        ));
    }
}
