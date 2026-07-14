package com.example.erp.notification.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for notification-service's persistence package.
 *
 * <p>This used to be forced: {@code libs/java-messaging}'s {@code OutboxJpaConfig}
 * declared an app-wide {@code @EnableJpaRepositories} that made Spring Boot's default
 * JPA repository auto-scanning back off. TASK-MONO-406 deleted that config (and the
 * {@code OutboxAutoConfiguration} that imported it), so the declaration below is now
 * this service's own choice: it scopes the notification / delivery / dedupe JPA
 * repositories + entities to this service's persistence package and binds them
 * deterministically regardless of auto-config ordering (mirrors masterdata /
 * read-model). notification remains a no-outbox terminal consumer — see
 * {@code NotificationServiceApplication}.
 */
@Configuration
@EnableJpaRepositories(basePackages =
        "com.example.erp.notification.infrastructure.persistence.jpa")
@EntityScan(basePackages =
        "com.example.erp.notification.infrastructure.persistence.jpa")
public class JpaConfig {
}
