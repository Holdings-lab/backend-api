package com.project.server.service.home;

import com.project.server.domain.PolicyEventEntity;
import com.project.server.domain.UserEntity;
import com.project.server.domain.UserWatchAssetEntity;
import com.project.server.dto.HomeDto;
import com.project.server.repository.PolicyEventJpaRepository;
import com.project.server.repository.UserJpaRepository;
import com.project.server.repository.UserWatchAssetRepository;
import com.project.server.service.auth.WatchAssetSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HomeService {

    private final UserJpaRepository userJpaRepository;
    private final PolicyEventJpaRepository policyEventJpaRepository;
    private final UserWatchAssetRepository userWatchAssetRepository;
        private final WatchAssetSelectionService watchAssetSelectionService;
        private final FeaturedEventStateService featuredEventStateService;

    public HomeDto.HomeResponse getHome(Long userId) {
        String name = resolveDisplayName(userId);
        PolicyEventEntity featuredEvent = policyEventJpaRepository.findById(101L)
                .or(() -> policyEventJpaRepository.findTopByOrderByCreatedAtDesc())
                .orElse(null);

        String featuredTitle = featuredEvent == null ? "FOMC 금리결정" : featuredEvent.getTitle();
        String featuredSummary = featuredEvent == null ? "예상 5.25% · 발표 전" : featuredEvent.getAnalysisSummary();
        String dDayText = formatDDay(featuredEvent == null ? LocalDateTime.now().plusHours(2).plusMinutes(15) : featuredEvent.getCreatedAt().plusHours(24));

        List<UserWatchAssetEntity> watchAssets = userWatchAssetRepository.findByUserIdOrderByDisplayOrderAsc(userId);
        List<HomeDto.WatchAssetImpact> watchAssetImpacts = watchAssets.isEmpty()
                ? watchAssetSelectionService.getWatchImpacts(userId)
                : watchAssets.stream()
                .map(asset -> HomeDto.WatchAssetImpact.builder()
                        .assetName(asset.getAssetName())
                        .signalText(asset.getSignalText())
                        .build())
                .toList();

        FeaturedEventStateService.FeaturedEventState featured = featuredEventStateService.getFeatured(userId);
        if (featured != null) {
            featuredTitle = featured.title();
            featuredSummary = featured.summary();
            dDayText = featured.dDayText();
        }

        return HomeDto.HomeResponse.builder()
                .userGreeting(HomeDto.UserGreeting.builder()
                        .displayName(name)
                        .headline("안녕하세요, " + name + "님")
                        .subtext("오늘 정책 이벤트와 연결된 자산 흐름을 빠르게 확인해보세요.")
                        .build())
                .featuredEvent(HomeDto.FeaturedEvent.builder()
                        .label("오늘의 핵심 이벤트")
                        .dDayText(dDayText)
                        .title(featuredTitle)
                        .tags(featured == null ? List.of("미국", "금리", "매우높음") : featured.tags())
                        .summary(featuredSummary)
                        .metaText("약 45초 · 4번의 탭 · 내 자산 " + watchAssetImpacts.size() + "개 연결")
                        .build())
                .learningCard(HomeDto.LearningCard.builder()
                        .label("오늘의 3분 학습")
                        .title("금리 인상 시 장기채가 흔들리는 이유")
                        .progressPercent(64)
                        .ctaText("이어서 학습하기")
                        .build())
                .watchAssetImpacts(watchAssetImpacts)
                .threeInsights(List.of(
                        HomeDto.InsightCard.builder().title("62%").subtitle("방향성").detail("성장주 하락").build(),
                        HomeDto.InsightCard.builder().title("74%").subtitle("변동성").detail("장기채 확대").build(),
                        HomeDto.InsightCard.builder().title("중간").subtitle("신뢰도").detail("유사 18건").build()
                ))
                .reasonPanel(HomeDto.ReasonPanel.builder()
                        .title("왜 이렇게 봤나")
                        .sourceText("Federal Reserve · 오늘 18:45 갱신")
                        .items(List.of(
                                HomeDto.ReasonItem.builder().rank(1).label("점도표 상향 가능성").score(40).build(),
                                HomeDto.ReasonItem.builder().rank(2).label("서비스 물가 끈적임").score(28).build(),
                                HomeDto.ReasonItem.builder().rank(3).label("고용 탄탄함").score(18).build()
                        ))
                        .ctaText("쉬운 설명 보기")
                        .build())
                .predictionReview(HomeDto.PredictionReview.builder()
                        .title("어제 예측 vs 실제")
                        .eventName("미국 CPI")
                        .summary("실제 발표 후 장기채 +1.1%, 예측 적중")
                        .ctaText("복기하기")
                        .build())
                .disclaimerText("본 앱은 정책 이벤트 학습용 시뮬레이터입니다. 실제 투자 판단을 사용하지 않습니다.")
                .build();
    }

    private String formatDDay(LocalDateTime targetTime) {
        Duration duration = Duration.between(LocalDateTime.now(), targetTime);
        if (duration.isNegative()) {
            return "D-0 00:00";
        }
        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();
        return String.format("D-0 %02d:%02d", hours, minutes);
    }

    private String resolveDisplayName(Long userId) {
        UserEntity user = userJpaRepository.findById(userId).orElse(null);
        if (user == null) {
            return "지웅";
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return "지웅";
    }
}
