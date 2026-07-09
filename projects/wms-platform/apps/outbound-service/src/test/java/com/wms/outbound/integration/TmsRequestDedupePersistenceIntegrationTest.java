package com.wms.outbound.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.port.out.TmsRequestDedupePort;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Real-Postgres regression for the TASK-BE-488 audit of {@code tms_request_dedupe}.
 * {@code saveSnapshot} manages its own {@code REQUIRES_NEW} transaction, so a
 * second call for the same requestId is a fresh delivery. The earlier
 * {@code repository.save()} routed to {@code merge()} — an UPDATE the W2
 * append-only trigger rejects at commit — so a re-fire threw; the
 * {@code INSERT … ON CONFLICT DO NOTHING} fix keeps the first snapshot and
 * no-ops the loser.
 */
class TmsRequestDedupePersistenceIntegrationTest extends OutboundServiceIntegrationBase {

    @Autowired
    private TmsRequestDedupePort dedupe;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM tms_request_dedupe");
    }

    @Test
    @DisplayName("re-fired requestId keeps the first snapshot (first-writer-wins, no throw)")
    void refiredRequestKeepsFirstSnapshot() {
        UUID requestId = UUID.randomUUID();
        String firstSnapshot = "{\"success\":true,\"vendorRequestId\":\"v-1\"}";
        String secondSnapshot = "{\"success\":true,\"vendorRequestId\":\"v-2\"}";

        dedupe.saveSnapshot(requestId, Instant.parse("2026-05-01T10:00:00Z"), firstSnapshot);
        // Second delivery must not throw and must not overwrite.
        dedupe.saveSnapshot(requestId, Instant.parse("2026-05-01T10:05:00Z"), secondSnapshot);

        Optional<String> stored = dedupe.findSnapshot(requestId);
        assertThat(stored).isPresent();
        assertThat(stored.get()).contains("v-1");

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tms_request_dedupe WHERE request_id = ?",
                Integer.class, requestId);
        assertThat(rows).isEqualTo(1);
    }
}
