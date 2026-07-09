package com.wms.notification.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.notification.application.port.out.AlertDedupePort;
import com.wms.notification.domain.delivery.DedupeOutcome;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real-Postgres regression for TASK-BE-488. Each {@code recordIfAbsent(...)}
 * call runs in its own committed transaction (mirroring two separate Kafka
 * deliveries), so the second call sees the first's committed dedupe row.
 * Against a real database the earlier {@code repository.save()} implementation
 * routed to {@code merge()} — a silent UPDATE that returned INSERTED on a
 * redelivery; the {@code INSERT … ON CONFLICT DO NOTHING} fix returns DUPLICATE.
 *
 * <p>The bug only manifests against a real DB (merge does SELECT-then-UPDATE),
 * so a mock-based unit test cannot catch it — this IT is the authoritative guard.
 */
class AlertDedupePersistenceIntegrationTest extends NotificationServiceIntegrationBase {

    @Autowired
    private AlertDedupePort dedupe;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("TRUNCATE TABLE notification_event_dedupe");
    }

    @Test
    @DisplayName("redelivered eventId is recorded once and reports DUPLICATE on replay")
    void redeliveredEventIdRecordsOnce() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        UUID eventId = UUID.randomUUID();

        AlertDedupePort.Result first = tx.execute(status ->
                dedupe.recordIfAbsent(eventId, "wms.inventory.low-stock.v1", DedupeOutcome.QUEUED));
        AlertDedupePort.Result second = tx.execute(status ->
                dedupe.recordIfAbsent(eventId, "wms.inventory.low-stock.v1", DedupeOutcome.QUEUED));

        assertThat(first).isEqualTo(AlertDedupePort.Result.INSERTED);
        assertThat(second).isEqualTo(AlertDedupePort.Result.DUPLICATE);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_event_dedupe WHERE event_id = ?",
                Integer.class, eventId);
        assertThat(rows).isEqualTo(1);
    }
}
