package com.example.fanplatform.notification.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for notification-service's persistence packages.
 *
 * <p>Declares scanning explicitly for the {@code Notification} aggregate + the
 * service-owned {@code processed_events} dedupe table. This used to be forced:
 * {@code libs/java-messaging}'s {@code OutboxJpaConfig} declared an app-wide
 * {@code @EnableJpaRepositories} that made Spring Boot's default JPA repository
 * auto-scanning back off. TASK-MONO-406 deleted that config (and the
 * {@code OutboxAutoConfiguration} that imported it), so the declaration below is now
 * this service's own choice — it keeps scanning scoped to this service's packages.
 * notification remains a no-outbox terminal consumer (see
 * {@code NotificationServiceApplication}). (Mirrors the membership / read-model
 * pattern.)
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
