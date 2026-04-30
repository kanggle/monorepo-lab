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
 * Exposes the auth_outbox_lag_seconds metric to Prometheus.
 *
 * Measures the age of the oldest unpublished outbox row in seconds.
 * When there are no pending rows, the lag is 0.
 */
@Slf4j
@Component
@Profile("!standalone")
@RequiredArgsConstructor
public class OutboxLagMetric {

    private final JdbcTemplate jdbcTemplate;
    private final MeterRegistry meterRegistry;

    @PostConstruct
    public void registerMetric() {
        Gauge.builder("auth_outbox_lag_seconds", this, OutboxLagMetric::calculateLag)
                .description("Age in seconds of the oldest unpublished outbox event")
                .register(meterRegistry);
    }

    double calculateLag() {
        try {
            Double lag = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), NOW()), 0) " +
                            "FROM outbox WHERE status = 'PENDING'",
                    Double.class
            );
            return lag != null ? lag : 0.0;
        } catch (DataAccessException e) {
            log.warn("Failed to calculate outbox lag metric: {}", e.getMessage());
            return -1.0;
        }
    }
}
