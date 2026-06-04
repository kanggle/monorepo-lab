package com.example.erp.readmodel.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for read-model-service's persistence package.
 *
 * <p>{@code libs/java-messaging}'s {@code OutboxJpaConfig} declares its own
 * {@code @EnableJpaRepositories}, which (when imported) suppresses Spring Boot's
 * default JPA repository auto-scanning. This service EXCLUDES the outbox
 * auto-config (read-model is no-outbox, E5 terminal — see
 * {@code ReadModelServiceApplication}), but we still declare scanning
 * explicitly so the projection + dedupe JPA repositories bind deterministically
 * regardless of auto-config ordering (mirrors masterdata / inventory-visibility).
 */
@Configuration
@EnableJpaRepositories(basePackages =
        "com.example.erp.readmodel.adapter.outbound.persistence.jpa")
@EntityScan(basePackages =
        "com.example.erp.readmodel.adapter.outbound.persistence.jpa")
public class JpaConfig {
}
