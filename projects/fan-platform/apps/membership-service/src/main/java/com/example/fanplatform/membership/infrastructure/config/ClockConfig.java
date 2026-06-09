package com.example.fanplatform.membership.infrastructure.config;

import com.example.fanplatform.membership.domain.time.ClockPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Clock wiring. The {@link ClockPort} bean truncates {@code now()} to
 * microseconds (§15) so the in-memory subscribe response equals the DB re-read.
 * A plain {@link Clock} bean (system UTC) is also exposed for any framework code
 * that needs it; tests can replace the {@code ClockPort} bean with a fixed one.
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
