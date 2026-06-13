package com.example.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * settlement-service (ADR-MONO-030 Step 4 facet b — marketplace seller settlement
 * / commission). A hybrid {@code event-consumer + rest-api} service: it builds an
 * append-only commission-accrual ledger from the order/payment event streams and
 * exposes operator-plane reads + a commission-rate admin.
 *
 * <p><b>Terminal consumer.</b> v1 publishes no events — the libs
 * {@code OutboxAutoConfiguration} / {@code OutboxMetricsAutoConfiguration} are not
 * on the classpath (settlement does not depend on {@code libs:java-messaging}). The
 * {@code processed_event} dedupe table is owned locally
 * ({@code com.example.settlement.infrastructure.persistence}). This mirrors the
 * finance ledger-service / erp read-model terminal-consumer precedent.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.example.settlement")
@EnableJpaRepositories(basePackages = "com.example.settlement.infrastructure.persistence")
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
