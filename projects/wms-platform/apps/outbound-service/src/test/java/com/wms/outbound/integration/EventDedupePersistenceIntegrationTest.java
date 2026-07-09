package com.wms.outbound.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.port.out.EventDedupePort;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real-Postgres regression for TASK-BE-488. Each {@code process(...)} call runs
 * in its own committed transaction (mirroring two separate Kafka deliveries), so
 * the second call sees the first's committed dedupe row. Against a real database
 * the earlier {@code repository.save()} implementation routed to
 * {@code merge()} — a silent UPDATE that let the second delivery re-run the
 * work; the {@code INSERT … ON CONFLICT DO NOTHING} fix makes it skip.
 *
 * <p>The bug only manifests against a real DB (merge does SELECT-then-UPDATE),
 * so a mock-based unit test cannot catch it — this IT is the authoritative guard.
 */
class EventDedupePersistenceIntegrationTest extends OutboundServiceIntegrationBase {

    @Autowired
    private EventDedupePort dedupe;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM outbound_event_dedupe");
    }

    @Test
    @DisplayName("redelivered eventId applies work exactly once (save→merge bug guard)")
    void redeliveredEventIdAppliesWorkExactlyOnce() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID eventId = UUID.randomUUID();
        AtomicInteger applied = new AtomicInteger();

        EventDedupePort.Outcome first = tx.execute(status ->
                dedupe.process(eventId, "inventory.reserved", applied::incrementAndGet));
        EventDedupePort.Outcome second = tx.execute(status ->
                dedupe.process(eventId, "inventory.reserved", applied::incrementAndGet));

        assertThat(first).isEqualTo(EventDedupePort.Outcome.APPLIED);
        assertThat(second).isEqualTo(EventDedupePort.Outcome.IGNORED_DUPLICATE);
        assertThat(applied.get()).isEqualTo(1);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbound_event_dedupe WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(rows).isEqualTo(1);
    }
}
