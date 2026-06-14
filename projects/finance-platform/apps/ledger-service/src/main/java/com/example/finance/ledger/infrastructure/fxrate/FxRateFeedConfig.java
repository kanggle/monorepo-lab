package com.example.finance.ledger.infrastructure.fxrate;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link FxRateFeedProperties} (23rd increment — TASK-FIN-BE-031, ADR-002). The service
 * has no {@code @ConfigurationPropertiesScan}, so the feed properties are bound here explicitly
 * (parity with how the other ledger config beans are declared in
 * {@code …infrastructure.config}). No bean is created when the feed is unconfigured — the
 * properties simply carry their net-zero defaults ({@code enabled=false}, {@code mode=noop}).
 */
@Configuration
@EnableConfigurationProperties(FxRateFeedProperties.class)
public class FxRateFeedConfig {
}
