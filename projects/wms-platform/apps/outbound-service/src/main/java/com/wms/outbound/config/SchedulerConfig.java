package com.wms.outbound.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} background tasks for non-test profiles.
 *
 * <p>The {@code test} profile excludes scheduling so unit/integration tests
 * trigger processors directly via {@code processBatch()} method calls.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulerConfig {
}
