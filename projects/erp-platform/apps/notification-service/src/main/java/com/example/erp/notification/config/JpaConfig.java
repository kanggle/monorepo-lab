package com.example.erp.notification.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for notification-service's persistence package.
 *
 * <p>{@code libs/java-messaging}'s {@code OutboxJpaConfig} declares its own
 * {@code @EnableJpaRepositories}, which (when imported) suppresses Spring Boot's
 * default JPA repository auto-scanning. This service EXCLUDES the outbox
 * auto-config (notification is a no-outbox terminal consumer — see
 * {@code NotificationServiceApplication}), but we still declare scanning
 * explicitly so the notification / delivery / dedupe JPA repositories bind
 * deterministically regardless of auto-config ordering (mirrors masterdata /
 * read-model).
 */
@Configuration
@EnableJpaRepositories(basePackages =
        "com.example.erp.notification.infrastructure.persistence.jpa")
@EntityScan(basePackages =
        "com.example.erp.notification.infrastructure.persistence.jpa")
public class JpaConfig {
}
