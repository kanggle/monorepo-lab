package com.example.payment.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for payment-service's own persistence package.
 *
 * <p>Required because {@code libs/java-messaging}'s {@code OutboxJpaConfig}
 * declares its own {@code @EnableJpaRepositories}, which suppresses Spring
 * Boot's default JPA repository auto-scanning from the
 * {@code @SpringBootApplication} base package. Without this, the
 * {@code PaymentJpaRepository} would not be picked up. Mirrors the pattern
 * used by scm-platform/procurement-service.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.payment.adapter.out.persistence")
@EntityScan(basePackages = "com.example.payment.adapter.out.persistence")
public class JpaConfig {
}
