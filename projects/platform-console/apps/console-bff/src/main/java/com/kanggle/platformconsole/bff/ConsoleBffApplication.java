package com.kanggle.platformconsole.bff;

import com.kanggle.platformconsole.bff.infrastructure.config.NotificationAggregatorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * console-bff Spring Boot entry point.
 *
 * <p>Stateless Backend-for-Frontend (BFF) for the unified operator console.
 * Governed by ADR-MONO-017 (ACCEPTED 2026-05-20). Hexagonal architecture per
 * {@code specs/services/console-bff/architecture.md}.
 *
 * <p>Hard invariants (D1-D8 byte-unchanged):
 * <ul>
 *   <li>No persistence (no JPA / Flyway / Redis / Kafka)</li>
 *   <li>No mutations at MVP (no Idempotency-Key / X-Operator-Reason)</li>
 *   <li>Per-domain credential dispatch via {@code CredentialSelectionPort} (D4.A)</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(NotificationAggregatorProperties.class)
public class ConsoleBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsoleBffApplication.class, args);
    }
}
