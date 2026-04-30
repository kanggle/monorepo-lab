package com.example.account.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for the account-service's own persistence package.
 *
 * <p>This is required because java-messaging's {@code OutboxJpaConfig}
 * declares its own {@code @EnableJpaRepositories}, which causes Spring Boot's
 * {@code JpaRepositoriesAutoConfiguration} to back off. Without this
 * configuration, service repositories such as {@code AccountJpaRepository}
 * are no longer auto-scanned. See TASK-BE-047.
 *
 * <p>Kept in its own {@code @Configuration} class (rather than on the main
 * application class) so that web-layer slice tests ({@code @WebMvcTest})
 * filter it out and do not trigger JPA wiring.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.example.account.infrastructure.persistence")
@EntityScan(basePackages = "com.example.account.infrastructure.persistence")
public class JpaConfig {
}
