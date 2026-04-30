package com.example.community.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for community-service's own persistence package.
 * Required because java-messaging's {@code OutboxJpaConfig} declares its own
 * {@code @EnableJpaRepositories}, which suppresses Spring Boot's default
 * JPA repository auto-scanning. See TASK-BE-047.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.community.infrastructure.persistence")
@EntityScan(basePackages = {
        "com.example.community.domain",
        "com.example.community.infrastructure.persistence"
})
public class JpaConfig {
}
