package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.application.port.out.DeliveryRepository;
import com.wms.notification.domain.delivery.NotificationDelivery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
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
 * <h2>Why this shape (and why it cannot hang CI)</h2>
 *
 * <p>An earlier two-thread + {@code CountDownLatch} version hung the CI
 * integration lane for 30 min: a thread stuck in a native JDBC lock-wait ignores
 * {@code shutdownNow()}, leaking an open transaction that then blocked the
 * {@code @AfterEach TRUNCATE} until the job timeout, and its scheduling races
 * contaminated the connection pool. This version is deterministic and bounded:
 * one dedicated connection locks a batch of rows and HOLDS them; the REAL
 * repository claim runs on a single daemon worker whose result is read with a
 * bounded {@link Future#get(long, TimeUnit)}. With a correct SKIP-LOCKED query
 * the claim never waits and returns at once. A regression to a plain
 * {@code FOR UPDATE} would block on the held rows — but the bounded {@code get}
 * turns that into a fast {@code TimeoutException} (RED), not a hang, and the
 * {@code finally} releases the held rows so the blocked query completes on the
 * discarded daemon thread rather than leaking a lock into {@code @AfterEach}.
 * The real repository method is exercised, so a dropped SKIP-LOCKED annotation
 * is caught.
 */
class DeliverySkipLockedClaimIntegrationTest extends NotificationServiceIntegrationBase {

    /** Bounded wait for the repository claim: a regressed (blocking) query fails here instead of hanging. */
    private static final int CLAIM_TIMEOUT_SECONDS = 10;

    @Autowired
    private DeliveryRepository deliveries;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanup() {
        // Guard the TRUNCATE with a tx-scoped lock_timeout on its own connection:
        // if the abandoned claimant thread from a regression run still transiently
        // holds a row lock, this fails fast+loud instead of hanging the lane. SET
        // LOCAL reverts on commit, so no session state leaks onto the pooled conn.
        jdbc.execute((Connection c) -> {
            boolean previousAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try (Statement st = c.createStatement()) {
                st.execute("SET LOCAL lock_timeout = '8s'");
                st.execute("TRUNCATE TABLE notification_delivery");
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(previousAutoCommit);
            }
            return null;
        });
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
    @DisplayName("repository claim skips rows already locked by another connection (SKIP LOCKED)")
    void repositoryClaimSkipsRowsLockedByAnotherConnection() throws Exception {
        Instant now = Instant.now();
        // Seed 6 due PENDING rows.
        for (int i = 0; i < 6; i++) {
            seedPending(now.minus(30 - i, ChronoUnit.MINUTES), null);
        }

        ExecutorService claimant = Executors.newSingleThreadExecutor(daemonFactory());
        // One dedicated connection locks the 3 oldest PENDING rows and HOLDS them
        // (open transaction) for the duration of the repository claim below.
        try (Connection locker = dataSource.getConnection()) {
            locker.setAutoCommit(false);
            List<UUID> locked = new ArrayList<>();
            try (PreparedStatement ps = locker.prepareStatement("""
                    SELECT id FROM notification_delivery
                     WHERE status = 'PENDING'
                     ORDER BY created_at
                     LIMIT 3
                     FOR UPDATE
                    """);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    locked.add(rs.getObject(1, UUID.class));
                }
            }
            assertThat(locked).hasSize(3);

            // The REAL repository claim runs WHILE `locker` holds those 3 rows. Its
            // @Lock(PESSIMISTIC_WRITE) + timeout=-2 (SKIP LOCKED) must skip the
            // locked rows and return the OTHER 3 without blocking. Read the result
            // with a bounded get so a regression (plain FOR UPDATE → block) fails
            // fast instead of hanging the lane.
            Future<List<UUID>> future = claimant.submit(() -> {
                TransactionTemplate tx = new TransactionTemplate(txManager);
                return tx.execute(s -> deliveries.findAndLockPendingDueForRetry(now, 100).stream()
                        .map(NotificationDelivery::id).toList());
            });

            List<UUID> claimed;
            try {
                claimed = future.get(CLAIM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } finally {
                // Release the held rows: unblocks the claim if it was waiting (regression
                // path) so it finishes on the discarded daemon thread — no leaked lock.
                locker.rollback();
            }

            // Exclusivity: the repository claim never returned a row locked by the
            // other connection, and it returned exactly the disjoint remainder.
            assertThat(Collections.disjoint(claimed, locked))
                    .as("repo claim %s must skip rows locked by the peer connection %s", claimed, locked)
                    .isTrue();
            assertThat(claimed).hasSize(3).doesNotHaveDuplicates();
        } finally {
            claimant.shutdownNow();
        }
    }

    private static ThreadFactory daemonFactory() {
        return r -> {
            Thread t = new Thread(r, "skip-locked-claimant");
            t.setDaemon(true);
            return t;
        };
    }
}
