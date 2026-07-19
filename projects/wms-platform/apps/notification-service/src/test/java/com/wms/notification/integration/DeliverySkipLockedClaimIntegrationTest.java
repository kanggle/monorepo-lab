package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.application.port.out.DeliveryRepository;
import com.wms.notification.domain.delivery.NotificationDelivery;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real-Postgres IT for {@code findAndLockPendingDueForRetry} — the
 * {@code SELECT … FOR UPDATE SKIP LOCKED} claim query behind the retry
 * scheduler (TASK-BE-528 AC-2).
 *
 * <p>The query carries {@code @Lock(PESSIMISTIC_WRITE)} +
 * {@code jakarta.persistence.lock.timeout = -2} (SKIP LOCKED) so that two
 * scheduler workers cannot double-fire the same PENDING delivery
 * (architecture.md § Concurrency Control). That exclusivity guarantee was
 * asserted only by code comments — never at runtime — and cannot be proven by
 * the in-memory fake (a plain list scan with no locking).
 *
 * <h2>Why two real transactions on two threads</h2>
 *
 * <p>SKIP LOCKED only manifests when two DB connections hold row locks
 * simultaneously — a single-threaded test proves nothing (memory:
 * {@code env_test_fixture_impossible_input_proves_nothing}). {@code @Transactional}
 * does not span threads, so each worker drives its own programmatic transaction
 * via {@link TransactionTemplate} on an {@link ExecutorService}, and a
 * {@link CountDownLatch} forces both to hold their claimed-row locks
 * concurrently (neither commits until both have run their locking SELECT). This
 * is the crux: without the latch, worker A could commit and release its locks
 * before worker B's SELECT ran, and B would legitimately see the rows —
 * masking a broken (non-SKIP-LOCKED) query.
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

    // --- plain claim semantics ------------------------------------------

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

    // --- SKIP LOCKED exclusivity ----------------------------------------

    @Test
    @DisplayName("two concurrent claimants never double-claim a PENDING row (SKIP LOCKED)")
    void twoConcurrentClaimantsNeverDoubleClaim() throws Exception {
        Instant now = Instant.now();
        // Seed a handful of due PENDING rows.
        for (int i = 0; i < 6; i++) {
            seedPending(now.minus(30 - i, ChronoUnit.MINUTES), null);
        }
        List<UUID> allDue = jdbc.queryForList(
                "SELECT id FROM notification_delivery WHERE status = 'PENDING'", UUID.class);
        assertThat(allDue).hasSize(6);

        // Both workers must hold their claimed-row locks at the SAME time.
        CountDownLatch bothClaimed = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<List<UUID>> worker = () -> {
                TransactionTemplate tx = new TransactionTemplate(txManager);
                return tx.execute(s -> {
                    List<UUID> ids = deliveries.findAndLockPendingDueForRetry(now, 100).stream()
                            .map(NotificationDelivery::id).toList();
                    // Signal we hold our locks, then wait for the peer so both
                    // transactions overlap before either commits/releases.
                    bothClaimed.countDown();
                    try {
                        bothClaimed.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return ids;
                });
            };

            Future<List<UUID>> fa = pool.submit(worker);
            Future<List<UUID>> fb = pool.submit(worker);
            List<UUID> a = fa.get(30, TimeUnit.SECONDS);
            List<UUID> b = fb.get(30, TimeUnit.SECONDS);

            // Exclusivity: no row is claimed by both workers.
            assertThat(Collections.disjoint(a, b))
                    .as("claims A=%s and B=%s must be disjoint (SKIP LOCKED)", a, b)
                    .isTrue();
            // No row is lost: the two disjoint claims partition all due rows.
            assertThat(a.size() + b.size()).isEqualTo(6);
            assertThat(a).doesNotHaveDuplicates();
            assertThat(b).doesNotHaveDuplicates();
        } finally {
            pool.shutdownNow();
        }
    }
}
