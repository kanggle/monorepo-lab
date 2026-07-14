package com.example.fanplatform.community.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for community-service's own persistence package.
 *
 * <p>Scopes repository / entity scanning to this service's own packages. It used to
 * be mandatory: {@code java-messaging}'s {@code OutboxJpaConfig} declared an app-wide
 * {@code @EnableJpaRepositories} that made Spring Boot's default JPA repository
 * auto-scanning back off, so every consumer had to re-declare its own. TASK-MONO-406
 * deleted that config, so this is now the service's own explicit choice, not a
 * workaround. Mirrors the same pattern used in GAP's community-service.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.fanplatform.community.infrastructure.jpa")
@EntityScan(basePackages = {
        "com.example.fanplatform.community.domain",
        "com.example.fanplatform.community.infrastructure.jpa"
})
public class JpaConfig {
}
