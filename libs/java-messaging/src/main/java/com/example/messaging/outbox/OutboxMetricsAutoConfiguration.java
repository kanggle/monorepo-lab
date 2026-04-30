package com.example.messaging.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers a default {@link OutboxFailureHandler} that increments a per-service
 * Micrometer counter on every Kafka publish failure.
 *
 * <p>The metric name is derived from {@code spring.application.name}:
 * {@code {prefix}_outbox_publish_failures} where the prefix is the application
 * name with the {@code -service} suffix stripped and hyphens replaced by underscores
 * (e.g. {@code auth-service} → {@code auth_outbox_publish_failures}).
 *
 * <p>Services that declare their own {@link OutboxFailureHandler} bean take
 * precedence — this auto-configuration backs off via {@link ConditionalOnMissingBean}.
 * Services without Micrometer on the classpath skip this configuration entirely.
 */
@AutoConfiguration(after = OutboxAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class OutboxMetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OutboxFailureHandler.class)
    OutboxFailureHandler defaultOutboxFailureHandler(
            MeterRegistry meterRegistry,
            @Value("${spring.application.name:application}") String appName) {
        String metricPrefix = appName.replaceAll("-service$", "").replace("-", "_");
        return (eventType, aggregateId, e) ->
                meterRegistry.counter(metricPrefix + "_outbox_publish_failures", "event_type", eventType).increment();
    }
}
