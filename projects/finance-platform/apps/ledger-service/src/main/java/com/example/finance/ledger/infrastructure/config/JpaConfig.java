package com.example.finance.ledger.infrastructure.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Explicit JPA scanning for ledger-service's persistence packages. The
 * {@code @SpringBootApplication} excludes the libs/java-messaging outbox
 * auto-config (so no foreign {@code @EnableJpaRepositories} is in play); this
 * declares the ledger's own scan explicitly (parity with account-service /
 * read-model-service).
 *
 * <p>The base package is {@code …infrastructure} (not just {@code …persistence.jpa})
 * so it covers BOTH the persistence adapters AND the 3rd-increment
 * {@code …infrastructure.outbox} package ({@code LedgerOutboxJpaRepository} +
 * {@code LedgerOutboxJpaEntity}) — a narrower scan leaves the outbox repository
 * unregistered (the bootstrap only surfaces in the Testcontainers IT, not the
 * Docker-free slice/unit runs).
 */
@Configuration
@EnableJpaRepositories(basePackages =
        "com.example.finance.ledger.infrastructure")
@EntityScan(basePackages = {
        "com.example.finance.ledger.domain",
        "com.example.finance.ledger.infrastructure"
})
public class JpaConfig {
}
