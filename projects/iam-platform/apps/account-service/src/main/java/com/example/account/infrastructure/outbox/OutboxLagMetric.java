package com.example.account.infrastructure.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Exposes the {@code account_outbox_lag_seconds} gauge (account-service observability.md
 * § 필수 메트릭, TASK-BE-662): the age in seconds of the oldest unpublished row in the v2
 * {@code account_outbox} table ({@code published_at IS NULL}); 0 when nothing is pending.
 *
 * <p>Same shape as auth-service's {@code infrastructure.messaging.OutboxLagMetric}, but
 * 🔴 <b>not</b> its query: that one reads the v1 {@code outbox} table
 * ({@code status = 'PENDING'}), which the v2 relay no longer writes — TASK-BE-450 records it
 * as "the retained-but-now-empty v1 table → reports 0". Here the v1 {@code outbox} survives
 * too (V0026 keeps it for the lib EntityScan), so copying that query would compile, run, and
 * report 0 forever.
 *
 * <p>{@code UTC_TIMESTAMP(6)}, not {@code NOW()}: {@code created_at} holds UTC wall time
 * (JDBC {@code serverTimezone=UTC}), while {@code NOW()} follows the session time zone.
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class OutboxLagMetric {

    static final String METRIC_NAME = "account_outbox_lag_seconds";

    static final String LAG_SQL =
            "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), UTC_TIMESTAMP(6)), 0) "
                    + "FROM account_outbox WHERE published_at IS NULL";

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void registerMetric() {
        Gauge.builder(METRIC_NAME, this, OutboxLagMetric::calculateLag)
                .description("Age in seconds of the oldest unpublished account outbox event")
                .register(meterRegistry);
    }

    double calculateLag() {
        try {
            Double lag = jdbcTemplate.queryForObject(LAG_SQL, Double.class);
            return lag != null ? lag : 0.0;
        } catch (DataAccessException e) {
            log.warn("Failed to calculate account outbox lag metric: {}", e.getMessage());
            // -1 = "could not measure" (same sentinel as the auth sibling) — never read it as "fresh".
            return -1.0;
        }
    }
}
