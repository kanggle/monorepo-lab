package com.example.fanplatform.membership.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for membership-service's own persistence package.
 *
 * <p>Scopes repository / entity scanning to this service's own packages. It used to
 * be mandatory: {@code java-messaging}'s {@code OutboxJpaConfig} declared an app-wide
 * {@code @EnableJpaRepositories} that made Spring Boot's default JPA repository
 * auto-scanning back off, so every consumer had to re-declare its own. TASK-MONO-406
 * deleted that config, so this is now the service's own explicit choice, not a
 * workaround. Mirrors the community-service pattern.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.fanplatform.membership.infrastructure.jpa")
@EntityScan(basePackages = {
        "com.example.fanplatform.membership.domain.membership",
        "com.example.fanplatform.membership.domain.idempotency",
        // membership_outbox v2 entity (TASK-FAN-BE-020). MUST be scanned or the
        // ddl-auto=validate boot fails — the mock-repo unit test won't catch a
        // missing scan, only the full-boot IT does (payment §27 lesson).
        "com.example.fanplatform.membership.infrastructure.jpa"
})
public class JpaConfig {
}
