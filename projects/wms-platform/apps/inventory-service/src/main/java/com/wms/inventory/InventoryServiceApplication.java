package com.wms.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * inventory-service supplies its OWN outbox stack (the {@code AbstractOutboxPublisher}-based
 * {@code @Component OutboxPublisher} + {@code OutboxWriterAdapter} over its
 * {@code InventoryOutboxJpaEntity}) and consumes no bean from a libs auto-config.
 *
 * <p><b>Formerly excluded {@code OutboxAutoConfiguration} (TASK-BE-432; the same fix
 * outbound-service applied in TASK-BE-333) — removed by TASK-MONO-406.</b> The original
 * trigger was the v1 libs {@code @Bean outboxPublisher}, which registered under the same
 * bean name as inventory's differently-typed {@code @Component} and threw
 * {@code BeanDefinitionOverrideException}; TASK-MONO-312 deleted those v1 beans. What kept
 * the exclude load-bearing afterwards was the auto-config's remaining payload — an
 * {@code @EntityScan} + {@code @EnableJpaRepositories} registering the lib's
 * {@code ProcessedEventJpaEntity}, which inventory never uses and has no
 * {@code processed_events} table for (under {@code ddl-auto: validate} that alone fails the
 * boot). MONO-406 deleted the entity and the auto-config, so the exclude has nothing left to
 * suppress.
 *
 * <p>The BE-432 defect went uncaught because no inventory full-context (Testcontainers) job
 * ran in CI and the slice/unit tests never load auto-configs — it surfaced only when the
 * service was brought up as a real app in the TASK-BE-431 fulfillment-loop demo. That is the
 * general shape of this defect class: it is invisible to the compiler and to unit tests.
 */
@SpringBootApplication
@EnableScheduling
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
