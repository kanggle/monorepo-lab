package com.example.scmplatform.procurement.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for procurement-service's own persistence package.
 *
 * <p>Required because {@code java-messaging}'s {@code OutboxJpaConfig} declares
 * its own {@code @EnableJpaRepositories}, which suppresses Spring Boot's
 * default JPA repository auto-scanning. Mirrors the same pattern used in
 * fan-platform/community-service.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.scmplatform.procurement.infrastructure.persistence.jpa")
@EntityScan(basePackages = {
        "com.example.scmplatform.procurement.domain",
        "com.example.scmplatform.procurement.infrastructure.persistence.jpa"
})
public class JpaConfig {
}
