package com.example.fanplatform.notification.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for notification-service's persistence packages.
 *
 * <p>{@code libs/java-messaging}'s {@code OutboxJpaConfig} declares its own
 * {@code @EnableJpaRepositories}, which (when imported) suppresses Spring Boot's
 * default JPA repository auto-scanning. This service EXCLUDES that outbox
 * auto-config (no-outbox terminal consumer — see
 * {@code NotificationServiceApplication}), so we declare scanning explicitly for
 * the {@code Notification} aggregate + the service-owned {@code processed_events}
 * dedupe table. (Mirrors the membership / read-model pattern.)
 */
@Configuration
@EnableJpaRepositories(basePackages = {
        "com.example.fanplatform.notification.infrastructure.jpa",
        "com.example.fanplatform.notification.infrastructure.messaging.idempotency"
})
@EntityScan(basePackages = {
        "com.example.fanplatform.notification.domain.notification",
        "com.example.fanplatform.notification.infrastructure.messaging.idempotency"
})
public class JpaConfig {
}
