package com.example.auth.infrastructure.messaging;

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
 * Exposes the {@code auth_outbox_lag_seconds} gauge (auth-service observability.md § 필수 메트릭):
 * the age in seconds of the oldest unpublished row in the v2 {@code auth_outbox} table
 * ({@code published_at IS NULL}); 0 when nothing is pending. {@code AuthOutboxLagHigh}
 * ({@code > 60}) alerts on it.
 *
 * <p>🔴 TASK-BE-663: until 2026-11-24 this read the v1 {@code outbox} table
 * ({@code status = 'PENDING'}). V0027 moved the relay to {@code auth_outbox} and kept the v1
 * table empty, so the gauge reported 0 forever and the alert could never fire. TASK-BE-450
 * had written that down as "harmless" — it was not, because the alert and the spec depend on
 * the value. Do not point this back at {@code outbox}.
 *
 * <p>{@code UTC_TIMESTAMP(6)}, not {@code NOW()}: {@code created_at} holds UTC wall time
 * (JDBC {@code serverTimezone=UTC}), while {@code NOW()} follows the session time zone.
 * Same query shape as account-service's {@code OutboxLagMetric} (TASK-BE-662).
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class OutboxLagMetric {

    static final String METRIC_NAME = "auth_outbox_lag_seconds";

    static final String LAG_SQL =
            "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), UTC_TIMESTAMP(6)), 0) "
                    + "FROM auth_outbox WHERE published_at IS NULL";

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void registerMetric() {
        Gauge.builder(METRIC_NAME, this, OutboxLagMetric::calculateLag)
                .description("Age in seconds of the oldest unpublished auth outbox event")
                .register(meterRegistry);
    }

    double calculateLag() {
        try {
            Double lag = jdbcTemplate.queryForObject(LAG_SQL, Double.class);
            return lag != null ? lag : 0.0;
        } catch (DataAccessException e) {
            log.warn("Failed to calculate auth outbox lag metric: {}", e.getMessage());
            // -1 = "could not measure" (same sentinel as account-service) — never read it as "fresh".
            return -1.0;
        }
    }
}
