package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.application.port.out.DeliveryRepository;
import com.wms.notification.domain.delivery.NotificationDelivery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real-Postgres IT for the {@code SELECT … FOR UPDATE SKIP LOCKED} claim behind
 * the retry scheduler (TASK-BE-528 AC-2).
 *
 * <p>{@code NotificationDeliveryJpaRepository.findPendingDueForRetry} carries
 * {@code @Lock(PESSIMISTIC_WRITE)} + {@code jakarta.persistence.lock.timeout = -2}
 * (SKIP LOCKED) so two scheduler workers cannot double-fire the same PENDING
 * delivery (architecture.md § Concurrency Control). That guarantee was asserted
 * only by code comments and cannot be proven by the in-memory fake.
 *
 * <p>{@link #claimReturnsOnlyPendingDueOrdered()} exercises the REAL repository
 * method (WHERE/ORDER/due-filtering). {@link #twoConnectionsWithSkipLockedNeverClaimTheSameRow()}
 * proves the DB-level SKIP-LOCKED exclusivity the repository relies on, using two
 * dedicated JDBC connections running the query's exact locking form.
 *
 * <h2>Why two dedicated connections (not threads)</h2>
 *
 * <p>SKIP LOCKED only manifests when two connections hold row locks
 * simultaneously (memory: {@code env_test_fixture_impossible_input_proves_nothing}).
 * An earlier two-thread + {@code CountDownLatch} version hung the CI lane for 30
 * min — a thread stuck in a native lock-wait ignores {@code shutdownNow()},
 * leaking an open transaction that blocked cleanup, and its scheduling races
 * contaminated the shared connection pool. This version is single-threaded and
 * deterministic: connection 1 locks a batch and holds it; connection 2 runs the
 * same SKIP-LOCKED query and, because it never waits, returns the disjoint
 * remainder at once. Both connections are explicitly rolled back and closed, so
 * nothing leaks into the pool or the {@code @AfterEach}.
 */
class DeliverySkipLockedClaimIntegrationTest extends NotificationServiceIntegrationBase {

    /**
     * The exact locking form of {@code findPendingDueForRetry} (see the repository's
     * {@code @Query} + {@code @Lock(PESSIMISTIC_WRITE)} + SKIP-LOCKED hint), run
     * directly so two connections can contend deterministically.
     */
    private static final String SKIP_LOCKED_CLAIM = """
            SELECT id FROM notification_delivery
             WHERE status = 'PENDING'
               AND (scheduled_retry_at IS NULL OR scheduled_retry_at <= ?)
             ORDER BY created_at
             LIMIT ?
             FOR UPDATE SKIP LOCKED
            """;

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

    // --- SKIP LOCKED exclusivity (two dedicated connections) ------------

    @Test
    @DisplayName("two connections with SKIP LOCKED never claim the same row")
    void twoConnectionsWithSkipLockedNeverClaimTheSameRow() throws Exception {
        Instant now = Instant.now();
        for (int i = 0; i < 6; i++) {
            seedPending(now.minus(30 - i, ChronoUnit.MINUTES), null);
        }

        // Two dedicated connections; connection 1 claims (and holds locks on) a
        // batch of 3, then connection 2 runs the same SKIP-LOCKED query while
        // connection 1 still holds its locks. SKIP LOCKED means connection 2 does
        // NOT block — it skips the locked rows and returns the disjoint remainder.
        try (Connection c1 = dataSource.getConnection();
                Connection c2 = dataSource.getConnection()) {
            c1.setAutoCommit(false);
            c2.setAutoCommit(false);
            try {
                List<UUID> claimA = claim(c1, now, 3);
                List<UUID> claimB = claim(c2, now, 3);

                assertThat(claimA).hasSize(3).doesNotHaveDuplicates();
                assertThat(claimB).hasSize(3).doesNotHaveDuplicates();
                assertThat(Collections.disjoint(claimA, claimB))
                        .as("A=%s and B=%s must be disjoint (SKIP LOCKED)", claimA, claimB)
                        .isTrue();
            } finally {
                c1.rollback();
                c2.rollback();
            }
        }
    }

    private List<UUID> claim(Connection c, Instant now, int limit) throws Exception {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(SKIP_LOCKED_CLAIM)) {
            // Bind as UTC OffsetDateTime (not java.sql.Timestamp) to match the seed
            // convention and avoid the host-TZ fixture trap.
            ps.setObject(1, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getObject(1, UUID.class));
                }
            }
        }
        return ids;
    }
}
