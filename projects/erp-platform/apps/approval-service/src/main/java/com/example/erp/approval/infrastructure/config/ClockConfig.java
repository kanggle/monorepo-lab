package com.example.erp.approval.infrastructure.config;

import com.example.erp.approval.application.port.outbound.ClockPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public ClockPort clockPort(Clock clock) {
        return () -> Instant.now(clock);
    }
}
