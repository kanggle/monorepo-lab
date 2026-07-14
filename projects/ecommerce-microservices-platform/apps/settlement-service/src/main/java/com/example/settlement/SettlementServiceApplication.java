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
 * settlement-service a producer for {@code settlement.period.closed.v1} via the v2
 * outbox pattern: its own {@code SettlementOutboxEntity} (extends the lib
 * {@code @MappedSuperclass OutboxRowEntity}) + {@code SettlementOutboxPublisher} relay,
 * both under {@code com.example.settlement} and picked up by this app's own
 * {@code @EntityScan}/{@code @EnableJpaRepositories}; the dispatcher runs via
 * {@code @EnableScheduling}. The accrual/reversal consume path is unchanged and
 * publishes nothing; inbound dedupe uses the locally-owned {@code processed_event}
 * table ({@code com.example.settlement.infrastructure.persistence}).
 *
 * <p><b>No longer excludes anything (TASK-MONO-406).</b> This class used to carry
 * {@code exclude = OutboxAutoConfiguration.class} because that lib auto-config imported an
 * {@code @EnableJpaRepositories} registering the lib's own {@code ProcessedEventJpaRepository}
 * — same simple name as settlement's, hence the same bean name, hence
 * {@code BeanDefinitionOverrideException} and a context that never started (TASK-BE-461;
 * TASK-BE-415 introduced it, latent until TASK-MONO-319 added a full-context IT). MONO-406
 * deleted the lib's dedupe entity/repository and the auto-config that carried them, so there
 * is nothing left to exclude. settlement's own processed-event (table
 * {@code processed_event}, {@code com.example.settlement.infrastructure.persistence}) is
 * unchanged and remains the only one.
 *
 * <p>{@code @EntityScan} stays scoped to {@code com.example.settlement} — the lib
 * {@code OutboxRowEntity} is a {@code @MappedSuperclass}, resolved via the entity hierarchy
 * rather than entity scanning, so no library package needs to be on the scan path.
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = "com.example.settlement")
@EnableJpaRepositories(basePackages = "com.example.settlement.infrastructure.persistence")
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
