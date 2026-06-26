package com.example.finance.account.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for account-service's persistence package.
 *
 * <p>An explicit declaration is required because account-service excludes the
 * libs {@code OutboxAutoConfiguration} (TASK-FIN-BE-045 — outbox v2): with an
 * explicit {@code @EnableJpaRepositories} present, Spring Boot's default JPA
 * auto-scanning backs off, so every repository package must be listed here.
 * Covers the domain persistence package plus the v2 {@code infrastructure.outbox}
 * package ({@code AccountOutboxJpaEntity} / {@code AccountOutboxJpaRepository}).
 * Mirrors scm-procurement-service / ledger-service.
 */
@Configuration
@EnableJpaRepositories(basePackages = {
        "com.example.finance.account.infrastructure.persistence.jpa",
        "com.example.finance.account.infrastructure.outbox"
})
@EntityScan(basePackages = {
        "com.example.finance.account.domain",
        "com.example.finance.account.infrastructure.persistence.jpa",
        "com.example.finance.account.infrastructure.outbox"
})
public class JpaConfig {
}
