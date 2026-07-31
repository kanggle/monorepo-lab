package com.example.fanplatform.notification.integration;

import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import com.example.fanplatform.notification.infrastructure.jpa.NotificationJpaRepository;
import com.example.fanplatform.notification.testsupport.EventIds;
import com.example.messaging.dedupe.EventDedupePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Postgres coverage for {@code EventDedupePortJpaAdapter} (TASK-FAN-BE-042,
 * ADR-MONO-058 § D7) — the mechanics a mock-based unit test cannot prove:
 * {@code INSERT … ON CONFLICT DO NOTHING} duplicate detection against a real
 * unique-constraint collision, cross-table transactional atomicity with the
 * {@code Notification} row, and the TOCTOU-closing behaviour under a genuine
 * concurrent race (two threads, two connections, same {@code eventId}).
 */
class EventDedupePortJpaAdapterIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    private EventDedupePort dedupePort;

    @Autowired
    private NotificationJpaRepository notifications;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        truncateAll();
    }

    @Test
    @DisplayName("first occurrence applies work; redelivery of the same eventId is IGNORED_DUPLICATE and does not re-run work")
    void redeliveredEventIdAppliesWorkExactlyOnce() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID eventId = UUID.fromString(EventIds.uuid("evt-adapter-dup"));
        AtomicInteger applied = new AtomicInteger();

        EventDedupePort.Outcome first = tx.execute(status ->
                dedupePort.process(eventId, "fan.membership.activated", applied::incrementAndGet));
        EventDedupePort.Outcome second = tx.execute(status ->
                dedupePort.process(eventId, "fan.membership.activated", applied::incrementAndGet));

        assertThat(first).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(second).isEqualTo(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        assertThat(applied.get()).isEqualTo(1);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Integer.class, eventId.toString());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("AC: work throws mid-way → the whole transaction rolls back — "
            + "neither the dedupe row nor the Notification row is persisted")
    void workExceptionRollsBackBothTables() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID eventId = UUID.fromString(EventIds.uuid("evt-rollback-1"));
        String notificationId = "notif-rollback-1";

        assertThatThrownBy(() -> tx.execute(status -> dedupePort.process(
                eventId, "fan.membership.activated", () -> {
                    notifications.save(Notification.create(
                            notificationId, "fan-platform", "acc-1", NotificationType.WELCOME,
                            "Welcome", "body", eventId.toString(), "fan.membership.activated",
                            "mem-1", null, Instant.now()));
                    throw new RuntimeException("simulated mid-way failure");
                })))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("simulated mid-way failure");

        Integer dedupeRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                Integer.class, eventId.toString());
        assertThat(dedupeRows).as("dedupe row must roll back with the rest of the transaction").isZero();
        assertThat(notifications.existsById(notificationId))
                .as("Notification row must roll back atomically with the dedupe row")
                .isFalse();
    }

    @Test
    @DisplayName("Edge Case: concurrent duplicate delivery (two threads, same eventId) → work applied exactly once, "
            + "one loser sees IGNORED_DUPLICATE (proves the fix closes the check-then-act TOCTOU window)")
    void concurrentDuplicateDeliveryAppliesWorkExactlyOnce() throws Exception {
        UUID eventId = UUID.fromString(EventIds.uuid("evt-adapter-race"));
        AtomicInteger applied = new AtomicInteger();
        CountDownLatch bothReady = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            TransactionTemplate tx = new TransactionTemplate(txManager);
            Future<EventDedupePort.Outcome> f1 = pool.submit(() -> {
                bothReady.countDown();
                bothReady.await(5, TimeUnit.SECONDS);
                return tx.execute(status ->
                        dedupePort.process(eventId, "fan.membership.activated", applied::incrementAndGet));
            });
            Future<EventDedupePort.Outcome> f2 = pool.submit(() -> {
                bothReady.countDown();
                bothReady.await(5, TimeUnit.SECONDS);
                return tx.execute(status ->
                        dedupePort.process(eventId, "fan.membership.activated", applied::incrementAndGet));
            });

            EventDedupePort.Outcome o1 = f1.get(10, TimeUnit.SECONDS);
            EventDedupePort.Outcome o2 = f2.get(10, TimeUnit.SECONDS);

            assertThat(applied.get()).as("work must run exactly once across both racing deliveries").isEqualTo(1);
            assertThat(java.util.List.of(o1, o2))
                    .as("exactly one APPLIED and one IGNORED_DUPLICATE — never both APPLIED (the TOCTOU bug"
                            + " a check-then-act implementation would allow)")
                    .containsExactlyInAnyOrder(
                            EventDedupePort.Outcome.APPLIED, EventDedupePort.Outcome.IGNORED_DUPLICATE);

            Integer rows = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM processed_events WHERE event_id = ?",
                    Integer.class, eventId.toString());
            assertThat(rows).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
