package com.example.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides the injectable {@link Clock} the stranded-refund sweeper / reconciler / recorder use
 * (TASK-BE-438) for deterministic backoff + attempt-cap testing without sleeps — the codebase
 * convention (cf. order-service {@code ClockConfig}, {@code AbstractOutboxPublisher}).
 */
@Configuration
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
