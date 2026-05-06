package com.example.scmplatform.procurement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * scm-platform procurement-service entry point.
 *
 * <p>Hexagonal architecture per
 * {@code projects/scm-platform/specs/services/procurement-service/architecture.md}
 * — domain (pure Java) ← application (use cases + ports) ← adapter (inbound
 * web / outbound persistence + supplier + messaging).
 *
 * <p>Dependencies: PostgreSQL ({@code scm_procurement} DB), Kafka (outbox
 * relay), Redis (idempotency cache), Resilience4j (supplier circuit breaker),
 * GAP IdP (OAuth2 Resource Server, RS256 JWKS).
 */
@SpringBootApplication
@EnableScheduling
public class ProcurementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcurementServiceApplication.class, args);
    }
}
