package com.example.finance.account.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for account-service's persistence package.
 *
 * <p>Scopes repository / entity scanning to this service's own packages: the domain
 * persistence package plus the v2 {@code infrastructure.outbox} package
 * ({@code AccountOutboxJpaEntity} / {@code AccountOutboxJpaRepository}). Because an
 * explicit {@code @EnableJpaRepositories} is present, Spring Boot's default JPA
 * repository auto-scanning backs off, so every repository package must be listed here.
 * TASK-MONO-406 deleted the libs {@code OutboxAutoConfiguration} /
 * {@code OutboxJpaConfig}, so this declaration is now the service's own choice rather
 * than a response to a library-imposed app-wide {@code @EnableJpaRepositories}.
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
