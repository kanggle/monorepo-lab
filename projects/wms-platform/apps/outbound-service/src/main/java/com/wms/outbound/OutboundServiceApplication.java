package com.wms.outbound;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * outbound-service supplies its OWN outbox publisher/writer (the
 * {@code AbstractOutboxPublisher}-based {@code @Component OutboxPublisher} +
 * {@code OutboxWriterAdapter} over its own {@code outbound_outbox} entity/repository) and
 * consumes no bean from a libs auto-config.
 *
 * <p><b>Formerly excluded {@code OutboxAutoConfiguration} (TASK-BE-333) — removed by
 * TASK-MONO-406.</b> The original trigger was the v1 libs {@code @Bean outboxPublisher},
 * which was {@code @ConditionalOnMissingBean} <em>by type</em> and so never saw outbound's
 * differently-typed {@code @Component} of the same name — both registered as
 * {@code "outboxPublisher"} and every {@code @SpringBootTest} IT and non-standalone startup
 * died with {@code BeanDefinitionOverrideException}. TASK-MONO-312 deleted those v1 beans;
 * what kept the exclude load-bearing afterwards was the auto-config's remaining payload, an
 * {@code @EntityScan} + {@code @EnableJpaRepositories} for the lib's
 * {@code ProcessedEventJpaEntity} that outbound neither uses nor has a table for. MONO-406
 * deleted both, so there is nothing left to exclude.
 */
@SpringBootApplication
public class OutboundServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OutboundServiceApplication.class, args);
    }
}
