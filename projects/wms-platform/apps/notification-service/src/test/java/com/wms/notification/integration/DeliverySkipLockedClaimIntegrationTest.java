package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.adapter.outbound.persistence.jpa.delivery.NotificationDeliveryJpaEntity;
import com.wms.notification.adapter.outbound.persistence.jpa.delivery.NotificationDeliveryJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * TASK-BE-529 — CI-robust delivery-claim integration coverage for the retry
 * scheduler's {@code FOR UPDATE SKIP LOCKED} claim query
 * ({@link NotificationDeliveryJpaRepository#findPendingDueForRetry}).
 *
 * <p>Two guarantees, one class:
 * <ol>
 *   <li><b>plain claim semantics</b> ({@link #claimReturnsOnlyPendingDueOrdered})
 *       — the REAL repository query returns only {@code PENDING} + due rows,
 *       ordered by {@code created_at}, against real Postgres.</li>
 *   <li><b>SKIP-LOCKED exclusivity</b>
 *       ({@link #twoClaimantsNeverDoubleClaimTheSamePendingRow}) — a row locked
 *       by one claimant is never double-claimed by a second, concurrent claim
 *       (architecture.md § Concurrency Control).</li>
 * </ol>
 *
 * <h2>Why this design cannot hang (TASK-BE-528 history)</h2>
 *
 * <p>BE-528 tried three in-process concurrency shapes; each passed locally but
 * <b>hung the bundled CI integration lane 28–30 min</b> because a claimant that
 * <i>blocked</i> on a lock had no bound, and its leaked open transaction then
 * blocked the {@code @AfterEach} cleanup until the 30-min job timeout. The
 * lesson (memory {@code env_ci_flake_is_a_hypothesis_not_a_verdict},
 * {@code env_wms_notification_seed_cluster_ci_flake}): a lock-contention IT MUST
 * bound every wait so a regression fails <b>fast and loud</b>, never at the job
 * timeout — and the CI lane, not local Windows Docker, is authoritative.
 *
 * <p>This test is deterministic and single-threaded — no {@code CountDownLatch},
 * no daemon threads, no {@code Future.get} entanglement:
 * <ul>
 *   <li>Claimant <b>A</b> is a dedicated raw JDBC connection with
 *       {@code SET lock_timeout = '5s'} issued <b>directly via a
 *       {@link Statement}</b> (not the Hibernate {@code SET LOCAL} path that
 *       BE-528 attempt #2 found unreliable). It locks exactly one claimable row
 *       ({@code FOR UPDATE SKIP LOCKED ... LIMIT 1}) and holds its transaction
 *       open.</li>
 *   <li>Claimant <b>B</b> is the <b>real repository query</b>, run in a
 *       transaction whose connection <i>also</i> carries {@code lock_timeout =
 *       '5s'} (set via {@link Session#doWork}). With {@code SKIP LOCKED} it
 *       skips A's locked row and returns immediately; if a regression drops the
 *       hint to a plain {@code FOR UPDATE}, B blocks and aborts in 5s — RED, not
 *       a hang (AC-2).</li>
 *   <li>A's lock is released by an explicit {@code rollback()} (and again on
 *       {@code close()}), so no lock survives into {@code @AfterEach}; cleanup
 *       is a bounded {@code DELETE}, never a {@code TRUNCATE} that could wait on
 *       a lingering lock.</li>
 * </ul>
 *
 * <p>Every method also carries a hard JUnit {@link Timeout} as a last-resort
 * backstop: even a design flaw fails in seconds, never at the job timeout.
 *
 * <p><b>Authority</b>: local Windows Testcontainers is FLAKY in this repo and is
 * NOT authoritative for a lock-contention test — the CI Linux lane is (AC-4).
 */
class DeliverySkipLockedClaimIntegrationTest extends NotificationServiceIntegrationBase {

    /** Fixed clock reference so seeded due/created times are deterministic. */
    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");

    /** Bound on the raw locker connection (A) — reliable on a direct connection. */
    private static final String LOCK_TIMEOUT = "5s";

    /**
     * Bound on claimant B's real-repository transaction. B uses {@code
     * statement_timeout}, NOT {@code lock_timeout}: Hibernate's
     * {@code PESSIMISTIC_WRITE} lock hint rewrites the session {@code
     * lock_timeout} per query (undoing a {@code SET lock_timeout} issued before
     * the claim), but {@code statement_timeout} is orthogonal to the lock hint,
     * so a blocked {@code FOR UPDATE} (a SKIP-LOCKED regression) aborts here in
     * a few seconds — RED, never the 30-min job timeout (AC-1/AC-2).
     */
    private static final String STATEMENT_TIMEOUT = "8s";

    @Autowired
    private NotificationDeliveryJpaRepository deliveryRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager txManager;

    @PersistenceContext
    private EntityManager entityManager;

    private TransactionTemplate tx;

    @AfterEach
    void cleanup() {
        // BOUNDED cleanup (defence-in-depth). The IT profile disables the retry-scheduler
        // poller (application-test.yml retry-poll-interval-ms), so no background claim
        // should hold a delivery-row lock here. But an unbounded DELETE is exactly how
        // BE-528 hung: the poller (then firing every 1s) held a FOR UPDATE lock on a
        // seeded row and this DELETE blocked on it forever (no lock_timeout on a pooled
        // connection) → the bundled lane hit the 30-min job timeout. A SET LOCAL
        // statement_timeout makes a stray lock fail FAST and loud (RED in 15s) instead.
        txTemplate().execute(status -> {
            entityManager.unwrap(Session.class).doWork(c -> {
                try (Statement s = c.createStatement()) {
                    s.execute("SET LOCAL statement_timeout = '15s'");
                    s.executeUpdate("DELETE FROM notification_delivery");
                }
            });
            return null;
        });
    }

    // -----------------------------------------------------------------------
    // (a) plain claim semantics — the REAL repository query.
    // -----------------------------------------------------------------------

    @Test
    @Timeout(30)
    @DisplayName("plain claim: findPendingDueForRetry returns only PENDING + due rows, ordered by created_at")
    void claimReturnsOnlyPendingDueOrdered() {
        UUID dueOld = seed("PENDING", NOW.minusSeconds(60), NOW.minusSeconds(300));
        UUID dueNewNullRetry = seed("PENDING", null, NOW.minusSeconds(120));
        seed("PENDING", NOW.plusSeconds(600), NOW.minusSeconds(200)); // not due yet
        seed("SUCCEEDED", NOW.minusSeconds(60), NOW.minusSeconds(400)); // terminal status
        seed("FAILED", null, NOW.minusSeconds(500)); // terminal status

        List<UUID> claimed = txTemplate().execute(status ->
                deliveryRepository.findPendingDueForRetry(NOW, PageRequest.of(0, 50)).stream()
                        .map(NotificationDeliveryJpaEntity::getId)
                        .toList());

        // Only the two PENDING+due rows, ordered by created_at (−300 before −120);
        // future-due, SUCCEEDED and FAILED rows are excluded.
        assertThat(claimed).containsExactly(dueOld, dueNewNullRetry);
    }

    // -----------------------------------------------------------------------
    // (b) SKIP-LOCKED exclusivity — bounded, deterministic, never hangs.
    // -----------------------------------------------------------------------

    @Test
    @Timeout(30)
    @DisplayName("SKIP-LOCKED exclusivity: a row locked by claimant A is skipped by claimant B's real-repo claim")
    void twoClaimantsNeverDoubleClaimTheSamePendingRow() throws Exception {
        UUID locked = seed("PENDING", NOW.minusSeconds(60), NOW.minusSeconds(120)); // older → claimed first
        UUID free = seed("PENDING", NOW.minusSeconds(60), NOW.minusSeconds(60)); // newer → left for B

        try (Connection a = dataSource.getConnection()) {
            a.setAutoCommit(false);
            setLockTimeout(a);
            assertThat(effectiveLockTimeout(a))
                    .as("a finite lock_timeout must actually be in effect on claimant A's "
                            + "connection on the CI runner (AC-1)")
                    .isNotEqualTo("0");

            // Claimant A locks exactly the first-by-created_at claimable row and holds it.
            List<UUID> lockedByA = claimForUpdateSkipLockedLimit1(a);
            assertThat(lockedByA)
                    .as("claimant A locks the first-by-created_at claimable row")
                    .containsExactly(locked);

            // Claimant B — the REAL repository query — runs while A holds `locked`.
            List<UUID> claimedByB = claimViaRealRepoBounded();

            // Exclusivity: B must NEVER see the row A holds, and must still make
            // progress on the free row. A regression that drops SKIP LOCKED makes
            // B block on `locked` and abort at lock_timeout (5s) → RED, not a hang.
            assertThat(claimedByB)
                    .as("SKIP LOCKED: B skips the row A holds and claims only the free one")
                    .containsExactly(free)
                    .doesNotContain(locked);

            a.rollback(); // release A's lock (also released on close)
        }
    }

    // -----------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------

    private TransactionTemplate txTemplate() {
        if (tx == null) {
            tx = new TransactionTemplate(txManager);
        }
        return tx;
    }

    /** Claimant B: the real repository query, bounded by statement_timeout so a non-skip-locked regression aborts fast. */
    private List<UUID> claimViaRealRepoBounded() {
        return txTemplate().execute(status -> {
            // Bound THIS transaction's connection via a direct Statement (reliable —
            // unlike the EntityManager SET LOCAL path BE-528 attempt #2 found flaky).
            // statement_timeout (not lock_timeout): the PESSIMISTIC_WRITE lock hint
            // rewrites lock_timeout per query, but leaves statement_timeout alone.
            entityManager.unwrap(Session.class).doWork(this::setStatementTimeout);
            assertThat((String) entityManager
                    .createNativeQuery("SELECT current_setting('statement_timeout')", String.class)
                    .getSingleResult())
                    .as("a finite statement_timeout must actually be in effect on B's connection "
                            + "on the CI runner (AC-1) — otherwise a blocked claim could hang")
                    .isNotEqualTo("0");
            return deliveryRepository.findPendingDueForRetry(NOW, PageRequest.of(0, 50)).stream()
                    .map(NotificationDeliveryJpaEntity::getId)
                    .toList();
        });
    }

    private void setLockTimeout(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("SET lock_timeout = '" + LOCK_TIMEOUT + "'");
        }
    }

    private void setStatementTimeout(Connection c) throws SQLException {
        // SET LOCAL: transaction-scoped, so it auto-resets at commit/rollback and never
        // leaks onto the pooled connection (a stray statement_timeout could kill a slow
        // sibling test). A direct Statement (not an EntityManager SET LOCAL query, which
        // BE-528 attempt #2 found unreliable) inside the active TransactionTemplate tx.
        try (Statement s = c.createStatement()) {
            s.execute("SET LOCAL statement_timeout = '" + STATEMENT_TIMEOUT + "'");
        }
    }

    private String effectiveLockTimeout(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT current_setting('lock_timeout')")) {
            rs.next();
            return rs.getString(1);
        }
    }

    /** Claimant A: lock exactly one claimable row via the same predicate + SKIP LOCKED, hold the tx open. */
    private List<UUID> claimForUpdateSkipLockedLimit1(Connection c) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT id FROM notification_delivery
                 WHERE status = 'PENDING'
                   AND (scheduled_retry_at IS NULL OR scheduled_retry_at <= ?::timestamptz)
                 ORDER BY created_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT 1
                """)) {
            ps.setString(1, NOW.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getObject("id", UUID.class));
                }
            }
        }
        return ids;
    }

    private UUID seed(String status, Instant scheduledRetryAt, Instant createdAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO notification_delivery
                  (id, event_id, source_topic, channel_id, recipient, delivery_idempotency_key,
                   payload_snapshot, status, attempt_count, scheduled_retry_at, last_error,
                   version, created_at, updated_at)
                VALUES (?, ?, 'wms.test.topic', 'slack:wms-alerts', 'ops@example.com', ?,
                   '{}'::jsonb, ?, 0, ?::timestamptz, NULL, 0, ?::timestamptz, ?::timestamptz)
                """,
                id,
                UUID.randomUUID(),
                "idem-" + id,
                status,
                scheduledRetryAt == null ? null : scheduledRetryAt.toString(),
                createdAt.toString(),
                createdAt.toString());
        return id;
    }
}
