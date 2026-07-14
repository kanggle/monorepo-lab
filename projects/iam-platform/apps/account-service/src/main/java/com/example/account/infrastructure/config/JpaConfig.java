package com.example.account.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for the account-service's own persistence package.
 *
 * <p>Scopes repository / entity scanning to this service's own persistence package
 * ({@code AccountJpaRepository}, {@code AccountOutboxJpaEntity}, the service-owned
 * {@code ProcessedEventJpaEntity}/{@code Repository}, …). It used to be mandatory:
 * java-messaging's {@code OutboxJpaConfig} declared an app-wide
 * {@code @EnableJpaRepositories} that made Spring Boot's
 * {@code JpaRepositoriesAutoConfiguration} back off, so without this class the service's
 * own repositories were silently not scanned (TASK-BE-047). TASK-MONO-406 deleted that
 * lib config, so this declaration is now the service's own choice, not a workaround.
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
