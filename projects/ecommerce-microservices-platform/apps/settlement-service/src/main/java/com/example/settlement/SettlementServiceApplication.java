package com.example.settlement;

import com.example.messaging.outbox.OutboxAutoConfiguration;
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
 * <p><b>Excludes {@link OutboxAutoConfiguration} (TASK-BE-461).</b> That lib auto-config
 * now does nothing but {@code @Import(OutboxJpaConfig)}, which registers the lib's
 * {@code ProcessedEventJpaEntity} (table {@code processed_events}) +
 * {@code ProcessedEventJpaRepository}. settlement keeps its OWN processed-event (table
 * {@code processed_event}); the lib copy collides — identical bean name
 * {@code processedEventJpaRepository} ({@code BeanDefinitionOverrideException}) and
 * identical JPA entity name {@code ProcessedEventJpaEntity}, with no
 * {@code processed_events} table for it. BE-415 introduced the collision (latent until
 * a full-context IT ran under TASK-MONO-319); excluding the auto-config drops the unused
 * lib copy. {@code @EntityScan} is correspondingly scoped to {@code com.example.settlement}
 * only — the lib {@code OutboxRowEntity} {@code @MappedSuperclass} is resolved via the
 * entity hierarchy, not entity scanning (mirrors
 * {@code com.wms.inventory.InventoryServiceApplication}, TASK-BE-432).
 */
@SpringBootApplication(exclude = OutboxAutoConfiguration.class)
@EnableScheduling
@EntityScan(basePackages = "com.example.settlement")
@EnableJpaRepositories(basePackages = "com.example.settlement.infrastructure.persistence")
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
