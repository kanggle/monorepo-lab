package com.example.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * settlement-service (ADR-MONO-030 Step 4 facet b — marketplace seller settlement
 * / commission). A hybrid {@code event-consumer + rest-api} service: it builds an
 * append-only commission-accrual ledger from the order/payment event streams and
 * exposes operator-plane reads + a commission-rate admin.
 *
 * <p><b>Producer for one event (TASK-BE-415).</b> The period-close increment makes
 * settlement-service a producer for {@code settlement.period.closed.v1}: it now
 * depends on {@code libs:java-messaging} ({@code OutboxAutoConfiguration} /
 * {@code OutboxMetricsAutoConfiguration} auto-enable via the lib's
 * {@code AutoConfiguration.imports}), scans the messaging {@code outbox} entity
 * ({@code @EntityScan} extended to {@code com.example.messaging}), and runs the
 * outbox dispatcher ({@code @EnableScheduling}). The accrual/reversal consume path
 * is unchanged and publishes nothing. The {@code processed_event} dedupe table is
 * still owned locally ({@code com.example.settlement.infrastructure.persistence}).
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {"com.example.settlement", "com.example.messaging"})
@EnableJpaRepositories(basePackages = "com.example.settlement.infrastructure.persistence")
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
