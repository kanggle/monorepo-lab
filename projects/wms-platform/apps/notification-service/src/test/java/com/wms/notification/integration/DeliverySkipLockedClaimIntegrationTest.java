package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.application.port.out.DeliveryRepository;
import com.wms.notification.domain.delivery.NotificationDelivery;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real-Postgres IT for the retry-scheduler claim query
 * {@code NotificationDeliveryJpaRepository.findPendingDueForRetry} (TASK-BE-528 AC-2).
 *
 * <p>Exercises the REAL repository method against a real Postgres Testcontainer:
 * the PENDING-status filter, the due-window predicate
 * ({@code scheduled_retry_at IS NULL OR <= now}), and {@code ORDER BY created_at}.
 * This replaces the in-memory fake's plain list scan with the actual JPA query
 * (its {@code @Query} + {@code @Lock(PESSIMISTIC_WRITE)} claim path).
 *
 * <p><b>Concurrency scope note.</b> The two-worker <em>SKIP-LOCKED exclusivity</em>
 * proof (no two claimants ever double-claim a PENDING row) is tracked separately
 * as <b>TASK-BE-529</b>: several in-process concurrency shapes (two threads + a
 * latch; a bounded daemon future; two dedicated JDBC connections) each passed
 * locally but hung or contaminated the shared connection pool on the bundled CI
 * integration lane. That exclusivity assertion needs a CI-robust harness (e.g. a
 * dedicated single-service integration job, or lock-timeout-bounded connections
 * validated on the runner) and is deferred rather than shipped flaky.
 */
class DeliverySkipLockedClaimIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    private DeliveryRepository deliveries;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("TRUNCATE TABLE notification_delivery");
    }

    // --- seeding ---------------------------------------------------------

    private UUID seedPending(Instant createdAt, Instant scheduledRetryAt) {
        return seed("PENDING", createdAt, scheduledRetryAt);
    }

    private UUID seed(String status, Instant createdAt, Instant scheduledRetryAt) {
        UUID id = UUID.randomUUID();
        // Bind TIMESTAMPTZ as UTC OffsetDateTime — NOT java.sql.Timestamp (which
        // pgjdbc interprets in the host JVM zone, the host-TZ fixture trap:
        // memory env_host_timezone_test_fixture_convention). Matches Hibernate's
        // UTC binding of the Instant `now` query param (jdbc.time_zone=UTC).
        jdbc.update("""
                INSERT INTO notification_delivery
                    (id, event_id, source_topic, channel_id, recipient,
                     delivery_idempotency_key, payload_snapshot, status,
                     attempt_count, scheduled_retry_at, last_error, version,
                     created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, NULL, 0, ?, ?)
                """,
                id, UUID.randomUUID(), "wms.inventory.alert.v1", "wms-alerts",
                "ops@example.com", "idem-" + id, "{\"text\":\"x\"}", status,
                scheduledRetryAt == null ? 0 : 1,
                scheduledRetryAt == null ? null : OffsetDateTime.ofInstant(scheduledRetryAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
        return id;
    }

    // --- plain claim semantics (real repository method) -----------------

    @Test
    @DisplayName("claim returns only PENDING + due rows, ordered by createdAt")
    void claimReturnsOnlyPendingDueOrdered() {
        Instant now = Instant.now();
        UUID oldest = seedPending(now.minus(30, ChronoUnit.MINUTES), null);
        UUID newer = seedPending(now.minus(10, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES));
        // Not due yet (scheduledRetryAt in the future) — excluded.
        seedPending(now.minus(5, ChronoUnit.MINUTES), now.plus(10, ChronoUnit.MINUTES));
        // Terminal — excluded.
        seed("SUCCEEDED", now.minus(20, ChronoUnit.MINUTES), null);
        seed("FAILED", now.minus(20, ChronoUnit.MINUTES), null);

        TransactionTemplate tx = new TransactionTemplate(txManager);
        List<UUID> claimed = tx.execute(s ->
                deliveries.findAndLockPendingDueForRetry(now, 100).stream()
                        .map(NotificationDelivery::id).toList());

        assertThat(claimed).containsExactly(oldest, newer);
    }
}
