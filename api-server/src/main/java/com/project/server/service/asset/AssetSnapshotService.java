package com.project.server.service.asset;

import com.project.server.domain.BrokerAccountEntity;
import com.project.server.domain.asset.AssetSnapshotType;
import com.project.server.domain.asset.UserAssetAthEntity;
import com.project.server.domain.asset.UserAssetSnapshotEntity;
import com.project.server.repository.BrokerAccountRepository;
import com.project.server.repository.asset.UserAssetAthRepository;
import com.project.server.repository.asset.UserAssetSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class AssetSnapshotService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final BrokerAccountRepository brokerAccountRepository;
    private final AssetMetricsService assetMetricsService;
    private final UserAssetSnapshotRepository snapshotRepository;
    private final UserAssetAthRepository athRepository;

    public void capturePreviousDaySnapshots() {
        LocalDate snapshotDate = LocalDate.now(KST).minusDays(1);
        List<Long> userIds = brokerAccountRepository.findByConnectionStatusIn(
                        List.of(BrokerAccountEntity.ConnectionStatus.CONNECTED)).stream()
                .map(BrokerAccountEntity::getUserId)
                .distinct()
                .toList();

        for (Long userId : userIds) {
            try {
                saveSnapshot(userId, AssetSnapshotType.PREVIOUS_DAY, snapshotDate);
            } catch (Exception e) {
                log.error("Failed to capture previous-day snapshot for user {}", userId, e);
            }
        }
    }

    public void scanAllTimeHighs() {
        List<Long> userIds = brokerAccountRepository.findByConnectionStatusIn(
                        List.of(BrokerAccountEntity.ConnectionStatus.CONNECTED)).stream()
                .map(BrokerAccountEntity::getUserId)
                .distinct()
                .toList();

        LocalDate today = LocalDate.now(KST);
        for (Long userId : userIds) {
            try {
                updateAth(userId, today);
            } catch (Exception e) {
                log.error("Failed to scan ATH for user {}", userId, e);
            }
        }
    }

    public void updateAth(Long userId, LocalDate asOfDate) {
        BigDecimal assetTotal = assetMetricsService.calculateAssetTotal(userId);
        if (assetTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        UserAssetAthEntity ath = athRepository.findById(userId)
                .orElse(UserAssetAthEntity.builder().userId(userId).build());

        if (ath.getAllTimeHighAmount() == null || assetTotal.compareTo(ath.getAllTimeHighAmount()) > 0) {
            ath.setAllTimeHighAmount(assetTotal);
            ath.setAllTimeHighDate(asOfDate);
            athRepository.save(ath);
        }
    }

    private void saveSnapshot(Long userId, AssetSnapshotType type, LocalDate snapshotDate) {
        BigDecimal assetTotal = assetMetricsService.calculateAssetTotal(userId);
        if (assetTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        UserAssetSnapshotEntity snapshot = snapshotRepository
                .findByUserIdAndSnapshotTypeAndSnapshotDate(userId, type, snapshotDate)
                .orElse(UserAssetSnapshotEntity.builder()
                        .userId(userId)
                        .snapshotType(type)
                        .snapshotDate(snapshotDate)
                        .build());

        snapshot.setAssetTotal(assetTotal);
        snapshot.setSnapshotAt(LocalDateTime.now(KST));
        snapshotRepository.save(snapshot);
    }
}
