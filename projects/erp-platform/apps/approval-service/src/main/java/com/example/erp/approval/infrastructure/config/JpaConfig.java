package com.example.erp.approval.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for approval-service's persistence package.
 *
 * <p>Required because {@code java-messaging}'s {@code OutboxJpaConfig} declares
 * its own {@code @EnableJpaRepositories}, which suppresses Spring Boot's default
 * JPA repository auto-scanning. Mirrors masterdata/finance/scm.
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
