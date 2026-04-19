package com.example.messaging.outbox;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration for Outbox and ProcessedEvent entities/repositories.
 *
 * This configuration registers the messaging-specific JPA repositories scoped
 * to the messaging package via basePackageClasses.
 *
 * IMPORTANT: declaring any explicit {@code @EnableJpaRepositories} in the
 * application context causes Spring Boot's
 * {@code JpaRepositoriesAutoConfiguration} to back off. As a result, the
 * consuming service's own repositories are NO LONGER auto-scanned from the
 * {@code @SpringBootApplication} base package. Every service that depends on
 * java-messaging MUST declare its own {@code @EnableJpaRepositories} (and
 * {@code @EntityScan}) for its persistence package in a dedicated
 * {@code @Configuration} class within the service's {@code infrastructure.config}
 * package (NOT on the main application class, to preserve {@code @WebMvcTest}
 * slice isolation).
 */
@Configuration
@EntityScan(basePackageClasses = {OutboxJpaEntity.class, ProcessedEventJpaEntity.class})
@EnableJpaRepositories(
        basePackageClasses = {OutboxJpaRepository.class, ProcessedEventJpaRepository.class},
        enableDefaultTransactions = false
)
public class OutboxJpaConfig {
}
