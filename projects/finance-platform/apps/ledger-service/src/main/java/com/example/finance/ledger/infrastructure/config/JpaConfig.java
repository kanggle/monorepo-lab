package com.example.finance.ledger.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for ledger-service's persistence packages. The
 * {@code @SpringBootApplication} excludes the libs/java-messaging outbox
 * auto-config (terminal consumer), so no foreign {@code @EnableJpaRepositories}
 * is in play; this declares the ledger's own scan explicitly (parity with
 * account-service / read-model-service).
 */
@Configuration
@EnableJpaRepositories(basePackages =
        "com.example.finance.ledger.infrastructure.persistence.jpa")
@EntityScan(basePackages = {
        "com.example.finance.ledger.domain",
        "com.example.finance.ledger.infrastructure.persistence.jpa"
})
public class JpaConfig {
}
