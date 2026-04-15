package com.project.server.repository;

import com.project.server.domain.WatchAssetCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WatchAssetCatalogRepository extends JpaRepository<WatchAssetCatalogEntity, Long> {
    List<WatchAssetCatalogEntity> findAllByOrderByDisplayOrderAsc();

    List<WatchAssetCatalogEntity> findByAssetNameIn(List<String> assetNames);
}
