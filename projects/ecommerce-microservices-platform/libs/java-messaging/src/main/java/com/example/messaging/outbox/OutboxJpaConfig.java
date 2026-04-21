package com.example.messaging.outbox;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for Outbox and ProcessedEvent entities/repositories.
 *
 * This configuration registers only the messaging-specific JPA repositories.
 * It uses basePackageClasses to limit the scope to only the messaging package,
 * so it does NOT interfere with the service's own JPA repository registration.
 *
 * Note: Spring Boot's JpaRepositoriesAutoConfiguration will still register
 * service-level repositories via its own @EnableJpaRepositories.
 * This config adds an additional @EnableJpaRepositories for the messaging package only.
 */
@Configuration
@EntityScan(basePackageClasses = {OutboxJpaEntity.class, ProcessedEventJpaEntity.class})
@EnableJpaRepositories(
        basePackageClasses = {OutboxJpaRepository.class, ProcessedEventJpaRepository.class},
        enableDefaultTransactions = false
)
public class OutboxJpaConfig {
}
