package com.wms.outbound.integration.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.saga.SagaSweeper;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import com.wms.outbound.integration.OutboundServiceIntegrationBase;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Testcontainers integration test for the saga sweeper (TASK-BE-050).
 *
 * <p>Drives the real {@link SagaSweeper} + {@link com.wms.outbound.application.saga.SagaRecoveryHandler}
 * + {@link SagaPersistencePort} adapter (Postgres backed) + {@link com.wms.outbound.adapter.out.event.outbox.OutboxReEmitterAdapter}
 * (Postgres backed) end-to-end. Asserts:
 *
 * <ol>
 *   <li>Stuck {@code REQUESTED} → fresh outbox row {@code outbound.picking.requested}</li>
 *   <li>Stuck {@code CANCELLATION_REQUESTED} → fresh outbox row {@code outbound.picking.cancelled}</li>
 *   <li>Stuck {@code SHIPPED} → fresh outbox row {@code outbound.shipping.confirmed}</li>
 *   <li>5-attempt exhaustion → saga {@code STUCK_RECOVERY_FAILED} + alert outbox row</li>
 *   <li>Re-emitted event has a fresh envelope {@code eventId} (ensures inventory
 *       consumer dedupe sees a new value)</li>
 * </ol>
 *
 * <p>The sweeper's {@code @Scheduled} annotation is disabled in {@code test}
 * profile (per {@link com.wms.outbound.config.SchedulerConfig}); we drive
 * {@link SagaSweeper#sweep()} directly so the test is deterministic.
 *
 * <p>The IT seeds an "original" outbox row for each saga (since the
 * sweeper clones from the original to preserve the payload — lines,
 * locations, quantities — that the saga itself does not retain). That
 * mirrors production: the saga and its first outbox emission are
 * co-committed in {@code ReceiveOrderService} / {@code CancelOrderService} /
 * {@code ConfirmShippingService}.
 */
class SagaSweeperIT extends OutboundServiceIntegrationBase {

    @Autowired private SagaSweeper sweeper;
    @Autowired private SagaPersistencePort sagaPersistence;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanState() {
        // Tests share Postgres/ Kafka state across classes — wipe what we touch.
        jdbc.update("DELETE FROM outbound_outbox");
        jdbc.update("DELETE FROM outbound_saga");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM outbound_outbox");
        jdbc.update("DELETE FROM outbound_saga");
    }

    // -- 1. Stuck REQUESTED -------------------------------------------------

    @Test
    @DisplayName("Stuck REQUESTED saga produces a fresh outbox row for outbound.picking.requested")
    void stuckRequested_reEmitsPickingRequested() {
        UUID sagaId = seedStuckSaga(SagaStatus.REQUESTED, 0);
        UUID originalEventId = seedOriginalOutboxRow(sagaId, "outbound.picking.requested",
                "outbound_saga", samplePayload(sagaId, "outbound.picking.requested"));

        sweeper.sweep();

        assertReEmitted(sagaId, "outbound.picking.requested", originalEventId);
        OutboundSaga reloaded = sagaPersistence.findById(sagaId).orElseThrow();
        assertThat(reloaded.reEmitCount()).isEqualTo(1);
        assertThat(reloaded.status()).isEqualTo(SagaStatus.REQUESTED);
    }

    // -- 2. Stuck CANCELLATION_REQUESTED ------------------------------------

    @Test
    @DisplayName("Stuck CANCELLATION_REQUESTED saga re-emits outbound.picking.cancelled")
    void stuckCancellationRequested_reEmitsPickingCancelled() {
        UUID sagaId = seedStuckSaga(SagaStatus.CANCELLATION_REQUESTED, 0);
        UUID originalEventId = seedOriginalOutboxRow(sagaId, "outbound.picking.cancelled",
                "outbound_saga", samplePayload(sagaId, "outbound.picking.cancelled"));

        sweeper.sweep();

        assertReEmitted(sagaId, "outbound.picking.cancelled", originalEventId);
    }

    // -- 3. Stuck SHIPPED ----------------------------------------------------

    @Test
    @DisplayName("Stuck SHIPPED saga re-emits outbound.shipping.confirmed")
    void stuckShipped_reEmitsShippingConfirmed() {
        UUID sagaId = seedStuckSaga(SagaStatus.SHIPPED, 0);
        UUID originalEventId = seedOriginalOutboxRow(sagaId, "outbound.shipping.confirmed",
                "shipment", samplePayload(sagaId, "outbound.shipping.confirmed"));

        sweeper.sweep();

        assertReEmitted(sagaId, "outbound.shipping.confirmed", originalEventId);
    }

    // -- 4. Exhaustion -------------------------------------------------------

    @Test
    @DisplayName("Sweeper cap exhaustion transitions saga to STUCK_RECOVERY_FAILED + alert event")
    void exhaustion_transitionsToStuckAndEmitsAlert() {
        // Pre-seed at MAX_ATTEMPTS - 1 so a single tick will hit the cap.
        UUID sagaId = seedStuckSaga(SagaStatus.REQUESTED, sweeper.maxAttempts() - 1);
        seedOriginalOutboxRow(sagaId, "outbound.picking.requested",
                "outbound_saga", samplePayload(sagaId, "outbound.picking.requested"));

        sweeper.sweep();

        OutboundSaga reloaded = sagaPersistence.findById(sagaId).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(SagaStatus.STUCK_RECOVERY_FAILED);
        assertThat(reloaded.reEmitCount()).isEqualTo(sweeper.maxAttempts());
        assertThat(reloaded.failureReason()).isEqualTo("saga_recovery_attempts_exhausted");

        // Alert outbox row exists.
        Long alertCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbound_outbox
                WHERE event_type = 'outbound.alert.saga.recovery.exhausted'
                  AND aggregate_id = ?
                """, Long.class, sagaId);
        assertThat(alertCount).isEqualTo(1L);

        // No fresh re-emission for picking.requested on the exhaustion tick.
        Long pickingRequestedCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbound_outbox
                WHERE event_type = 'outbound.picking.requested'
                  AND aggregate_id = ?
                """, Long.class, sagaId);
        // Only the original seed row — no clone.
        assertThat(pickingRequestedCount).isEqualTo(1L);
    }

    // -- 5. Idempotency at consumer (re-emit produces fresh envelope id) ----

    @Test
    @DisplayName("Re-emitted event carries a fresh envelope eventId (consumer dedupe sees new value)")
    void reEmittedEventHasFreshEnvelopeId() {
        UUID sagaId = seedStuckSaga(SagaStatus.REQUESTED, 0);
        UUID originalEventId = seedOriginalOutboxRow(sagaId, "outbound.picking.requested",
                "outbound_saga", samplePayload(sagaId, "outbound.picking.requested"));

        sweeper.sweep();

        // Find the cloned row.
        Map<String, Object> cloned = jdbc.queryForMap("""
                SELECT id, payload FROM outbound_outbox
                WHERE event_type = 'outbound.picking.requested'
                  AND aggregate_id = ?
                  AND id <> ?
                """, sagaId, originalEventId);

        UUID clonedId = (UUID) cloned.get("id");
        String clonedPayload = (String) cloned.get("payload");
        assertThat(clonedId).isNotEqualTo(originalEventId);
        // The envelope's eventId should match the row PK (not the original).
        assertThat(clonedPayload).contains("\"eventId\":\"" + clonedId + "\"");
        assertThat(clonedPayload).doesNotContain("\"eventId\":\"" + originalEventId + "\"");
        // Sweeper sets the envelope actorId.
        assertThat(clonedPayload).contains("\"actorId\":\"system:saga-sweeper\"");
    }

    // -- helpers ------------------------------------------------------------

    /**
     * Insert a saga that has been "stuck" for 10 minutes (well past the
     * default 5-min grace period). The DB clock is used by the sweeper's
     * findStuck query, so we set updated_at via JDBC to a time relative
     * to {@code now()}.
     */
    private UUID seedStuckSaga(SagaStatus status, int reEmitCount) {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Instant tenMinAgo = Instant.now().minusSeconds(600);
        jdbc.update("""
                INSERT INTO outbound_saga
                    (id, order_id, status, picking_request_id,
                     created_at, updated_at, version, re_emit_count)
                VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                """,
                sagaId, orderId, status.name(), sagaId,
                java.sql.Timestamp.from(Instant.parse("2026-05-10T09:00:00Z")),
                java.sql.Timestamp.from(tenMinAgo),
                reEmitCount);
        return sagaId;
    }

    private UUID seedOriginalOutboxRow(UUID sagaId, String eventType,
                                       String aggregateType, String payload) {
        UUID rowId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbound_outbox
                    (id, aggregate_type, aggregate_id, event_type, event_version,
                     payload, partition_key, status, created_at, retry_count)
                VALUES (?, ?, ?, ?, 'v1', CAST(? AS jsonb), ?, 'PUBLISHED',
                        ?, 0)
                """,
                rowId, aggregateType.toUpperCase(), sagaId, eventType,
                payload, sagaId.toString(),
                java.sql.Timestamp.from(Instant.now().minusSeconds(700)));
        // Mark as published since the IT scenario is "lost in flight after
        // publish but consumer never replied" — outbox row already published.
        jdbc.update("UPDATE outbound_outbox SET published_at = now() WHERE id = ?", rowId);
        return rowId;
    }

    private void assertReEmitted(UUID sagaId, String eventType, UUID originalEventId) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbound_outbox
                WHERE event_type = ?
                  AND aggregate_id = ?
                """, Long.class, eventType, sagaId);
        assertThat(total).as("original + clone").isEqualTo(2L);

        // The clone is PENDING and has a new id distinct from the original.
        Long clones = jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbound_outbox
                WHERE event_type = ?
                  AND aggregate_id = ?
                  AND id <> ?
                  AND published_at IS NULL
                  AND status = 'PENDING'
                """, Long.class, eventType, sagaId, originalEventId);
        assertThat(clones).as("fresh PENDING clone count").isEqualTo(1L);
    }

    /**
     * Minimal JSON envelope — the sweeper only rewrites {@code eventId},
     * {@code actorId}, {@code occurredAt}; everything else is preserved
     * verbatim.
     */
    private static String samplePayload(UUID sagaId, String eventType) {
        return """
                {
                  "eventId": "00000000-0000-7000-8000-000000000001",
                  "eventType": "%s",
                  "eventVersion": 1,
                  "occurredAt": "2026-05-10T09:00:00.000Z",
                  "producer": "outbound-service",
                  "aggregateType": "outbound_saga",
                  "aggregateId": "%s",
                  "traceId": null,
                  "actorId": "user-test",
                  "payload": { "sagaId": "%s" }
                }
                """.formatted(eventType, sagaId, sagaId);
    }
}
