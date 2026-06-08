package com.example.product.infrastructure.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** skuId → skuCode reverse-identity snapshot (ADR-MONO-022 §D4 v2(b)). */
public interface WmsSkuSnapshotRepository extends JpaRepository<WmsSkuSnapshotEntity, UUID> {
}
