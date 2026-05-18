package com.example.finance.account.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for account-service's persistence package.
 *
 * <p>Required because {@code java-messaging}'s {@code OutboxJpaConfig} declares
 * its own {@code @EnableJpaRepositories}, which suppresses Spring Boot's
 * default JPA repository auto-scanning. Mirrors scm-procurement-service.
 */
@Configuration
@EnableJpaRepositories(basePackages =
        "com.example.finance.account.infrastructure.persistence.jpa")
@EntityScan(basePackages = {
        "com.example.finance.account.domain",
        "com.example.finance.account.infrastructure.persistence.jpa"
})
public class JpaConfig {
}
