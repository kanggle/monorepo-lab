package com.example.erp.notification.config;

import com.example.erp.notification.application.port.outbound.ClockPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Timestamps are truncated to **microseconds** so an in-memory {@code Instant}
     * is byte-stable across a MySQL {@code DATETIME(6)} round-trip (which stores
     * microsecond precision). Without this, a freshly-set {@code readAt}/{@code
     * createdAt} (nanosecond `Instant.now`) differs from the same value re-read
     * from the DB at microsecond precision — the idempotent mark-read response
     * (in-memory on the first call, DB-reloaded on the re-mark) would then carry
     * two different sub-microsecond renderings of the *same* logical instant. The
     * Docker-free `:check` (no DB round-trip) cannot surface this; the
     * Testcontainers IT (real MySQL) does (§ NotificationEndToEndIntegrationTest).
     */
    @Bean
    public ClockPort clockPort(Clock clock) {
        return () -> Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
