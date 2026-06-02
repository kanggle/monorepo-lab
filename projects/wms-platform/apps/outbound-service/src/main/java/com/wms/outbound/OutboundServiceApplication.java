package com.wms.outbound;

import com.example.messaging.outbox.OutboxAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TASK-BE-333: exclude the shared {@link OutboxAutoConfiguration}. outbound-service
 * supplies its OWN outbox publisher/writer (the {@code AbstractOutboxPublisher}-based
 * {@code @Component OutboxPublisher} + {@code OutboxWriterAdapter} over its own
 * {@code outbound_outbox} entity/repository) and does not use any libs auto-config
 * bean. The libs {@code @Bean outboxPublisher} (type {@code com.example.messaging.
 * outbox.OutboxPublisher}, {@code @ConditionalOnMissingBean} by type) does NOT see
 * outbound's differently-typed {@code outboxPublisher} {@code @Component}, so under
 * any non-{@code standalone} profile both register under the SAME bean name
 * "outboxPublisher" → {@code BeanDefinitionOverrideException} (broke every
 * {@code @SpringBootTest} IT and non-standalone startup). Excluding the auto-config
 * is safe (no libs outbox bean / {@code OutboxJpaConfig} is referenced by outbound).
 */
@SpringBootApplication(exclude = OutboxAutoConfiguration.class)
public class OutboundServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutboundServiceApplication.class, args);
    }
}
