package com.example.erp.readmodel.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for read-model-service's persistence package.
 *
 * <p>This used to be forced: {@code libs/java-messaging}'s {@code OutboxJpaConfig}
 * declared an app-wide {@code @EnableJpaRepositories} that made Spring Boot's default
 * JPA repository auto-scanning back off. TASK-MONO-406 deleted that config (and the
 * {@code OutboxAutoConfiguration} that imported it), so the declaration below is now
 * this service's own choice: it scopes the projection + dedupe JPA repositories +
 * entities to this service's persistence package and binds them deterministically
 * regardless of auto-config ordering (mirrors masterdata / inventory-visibility).
 * read-model remains no-outbox, E5 terminal — see {@code ReadModelServiceApplication}.
 */
@Configuration
@EnableJpaRepositories(basePackages =
        "com.example.erp.readmodel.adapter.outbound.persistence.jpa")
@EntityScan(basePackages =
        "com.example.erp.readmodel.adapter.outbound.persistence.jpa")
public class JpaConfig {
}
