package com.example.fanplatform.notification.infrastructure.config;

import com.example.fanplatform.notification.domain.time.ClockPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Clock wiring. The {@link ClockPort} bean truncates {@code now()} to
 * microseconds (§15) so an in-memory value equals the Postgres TIMESTAMPTZ
 * re-read. Tests can replace the {@code ClockPort} bean with a fixed one.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ClockPort clockPort(Clock clock) {
        return () -> Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}
