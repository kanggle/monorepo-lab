package com.example.fanplatform.membership.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for membership-service's own persistence package.
 * Required because {@code java-messaging}'s {@code OutboxJpaConfig} declares its
 * own {@code @EnableJpaRepositories}, which suppresses Spring Boot's default JPA
 * repository auto-scanning (see {@code OutboxJpaConfig} javadoc). Mirrors the
 * community-service pattern.
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
