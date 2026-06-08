package com.example.product.infrastructure.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Idempotent-consumer dedupe (T8) for the wms reconciliation leg (ADR-MONO-022 §D4 v2(b)). */
public interface WmsProcessedEventRepository extends JpaRepository<WmsProcessedEventEntity, UUID> {
}
