package com.example.erp.approval.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for approval-service's persistence package.
 *
 * <p>Scopes repository / entity scanning to this service's own packages. It used
 * to be mandatory: {@code java-messaging}'s {@code OutboxJpaConfig} declared an
 * app-wide {@code @EnableJpaRepositories} that made Spring Boot's default JPA
 * repository auto-scanning back off, so every consumer had to re-declare its own.
 * TASK-MONO-406 deleted that config, so this is now the service's own explicit
 * choice, not a workaround. Mirrors masterdata/finance/scm.
 */
@Configuration
@EnableJpaRepositories(basePackages =
        "com.example.erp.approval.infrastructure.persistence.jpa")
@EntityScan(basePackages = {
        "com.example.erp.approval.domain",
        "com.example.erp.approval.infrastructure.persistence.jpa"
})
public class JpaConfig {
}
