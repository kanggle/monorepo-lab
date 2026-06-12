package com.example.erp.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Wiring for the v2.0 external-channel delivery (TASK-ERP-BE-020): registers the
 * {@link ExternalNotificationProperties}, enables Spring scheduling (inert unless the
 * {@code DeliveryRetryScheduler} bean is active — gated on
 * {@code …external.retry.enabled}), and provides the {@link RandomGenerator} used by
 * {@code RetryBackoffPolicy} for ±20% jitter. All of this is net-zero by default — no
 * external delivery is created or attempted unless {@code …external.enabled=true}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ExternalNotificationProperties.class)
public class SchedulingConfig {

    @Bean
    public RandomGenerator deliveryJitterRandom() {
        return new SecureRandom();
    }
}
