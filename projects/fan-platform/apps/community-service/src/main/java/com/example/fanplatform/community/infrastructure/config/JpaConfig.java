package com.example.fanplatform.community.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for community-service's own persistence package.
 * Required because {@code java-messaging}'s {@code OutboxJpaConfig} declares
 * its own {@code @EnableJpaRepositories}, which suppresses Spring Boot's
 * default JPA repository auto-scanning. Mirrors the same pattern used in
 * GAP's community-service.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.fanplatform.community.infrastructure.jpa")
@EntityScan(basePackages = {
        "com.example.fanplatform.community.domain",
        "com.example.fanplatform.community.infrastructure.jpa"
})
public class JpaConfig {
}
