package com.example.shipping.infrastructure.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ProcessedCarrierWebhookJpaRepository
        extends JpaRepository<ProcessedCarrierWebhookJpaEntity, String> {

    /**
     * Delete up to {@code batchSize} of the OLDEST dedup markers whose {@code received_at} is
     * STRICTLY older than {@code cutoff} (TASK-BE-361 / ADR-007 D5-4 retention sweep).
     *
     * <p><b>Strict {@code <} boundary.</b> A marker with {@code received_at == cutoff} is
     * RETAINED (only {@code received_at < cutoff} is selected). This is what keeps in-window
     * markers alive so a carrier re-delivery of the same {@code delivery_id} within the
     * retention window stays a DUPLICATE no-op (AC-2 idempotency; BE-294 registerIfFirst is
     * unchanged).
     *
     * <p><b>Bounded batch, own transaction (F2).</b> The {@code LIMIT}ed inner select (ordered
     * by {@code received_at}, index-supported by {@code idx_processed_carrier_webhooks_received_at})
     * deletes at most {@code batchSize} rows per call, and {@code @Transactional} here commits
     * each batch independently — so the sweep loop never opens one giant runaway transaction.
     * The {@code IN (SELECT ... LIMIT ...)} indirection is required because PostgreSQL does not
     * support {@code LIMIT} directly on {@code DELETE}.
     *
     * @param cutoff    markers strictly older than this instant are eligible for deletion
     * @param batchSize maximum rows deleted in this single call
     * @return number of rows actually deleted (a return {@code < batchSize} means drained)
     */
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM processed_carrier_webhooks WHERE delivery_id IN "
            + "(SELECT delivery_id FROM processed_carrier_webhooks WHERE received_at < :cutoff "
            + "ORDER BY received_at LIMIT :batchSize)", nativeQuery = true)
    int deleteBatchOlderThan(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
}
