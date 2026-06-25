package com.wms.inventory;

import com.example.messaging.outbox.OutboxAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TASK-BE-432: exclude the shared {@link OutboxAutoConfiguration} — the same fix
 * outbound-service applied in TASK-BE-333. inventory-service supplies its OWN outbox
 * stack (the {@code AbstractOutboxPublisher}-based {@code @Component OutboxPublisher} +
 * {@code OutboxWriterAdapter} over its {@code InventoryOutboxJpaEntity}) and uses no
 * libs auto-config bean. The libs {@code @Bean outboxPublisher} (type
 * {@code com.example.messaging.outbox.OutboxPublisher}, {@code @ConditionalOnMissingBean}
 * by type) does NOT see inventory's differently-typed {@code outboxPublisher}
 * {@code @Component}, so under any non-{@code standalone} profile both register under
 * the SAME bean name {@code "outboxPublisher"} → {@code BeanDefinitionOverrideException}
 * and the context fails to start.
 *
 * <p>Never caught because no inventory full-context (Testcontainers) job runs in CI and
 * the slice/unit tests don't load the auto-config; it surfaced only when the service was
 * brought up as a real app in the TASK-BE-431 ecommerce↔wms fulfillment-loop demo.
 */
@SpringBootApplication(exclude = OutboxAutoConfiguration.class)
@EnableScheduling
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
