package com.example.security.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for the security-service's own persistence package
 * ({@code SecurityOutboxJpaEntity}, the service-owned {@code ProcessedEventJpaEntity} /
 * {@code ProcessedEventJpaRepository}, …).
 *
 * <p>Scopes repository / entity scanning to this service's own packages. It used to be
 * mandatory: java-messaging's {@code OutboxJpaConfig} declared an app-wide
 * {@code @EnableJpaRepositories} that made Spring Boot's default JPA repository
 * auto-scanning back off (TASK-BE-047). TASK-MONO-406 deleted that lib config, so this
 * declaration is now the service's own choice, not a workaround.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.security.infrastructure.persistence")
@EntityScan(basePackages = "com.example.security.infrastructure.persistence")
public class JpaConfig {
}
