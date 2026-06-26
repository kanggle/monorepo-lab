package com.wms.master.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.adapter.out.messaging.EventEnvelopeSerializer;
import com.wms.master.adapter.out.messaging.OutboxDomainEventAdapter;
import com.wms.master.adapter.out.persistence.outbox.MasterOutboxRepository;
import com.wms.master.application.port.out.DomainEventPort;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outbox wiring for master-service (TASK-BE-438, outbox v2).
 *
 * <p>The publisher itself is the {@code @Component}
 * {@link com.wms.master.adapter.out.messaging.MasterOutboxPublisher} (a thin
 * {@code AbstractOutboxPublisher} subclass). This config supplies the two
 * infrastructure beans that subclass needs by constructor injection
 * ({@link Clock}, {@link TransactionTemplate}) plus the envelope serializer and
 * the {@link DomainEventPort} write adapter.
 *
 * <p>The v1 stack — {@code MasterOutboxPollingScheduler}, the bespoke
 * {@code OutboxMetrics} bean, and the lib {@code OutboxWriter}-based write path —
 * is gone. The lib {@code OutboxAutoConfiguration} is intentionally retained
 * (not excluded): its {@code OutboxJpaConfig} EntityScan is what keeps the v1
 * {@code outbox}/{@code processed_events} tables required under
 * {@code ddl-auto=validate}; see {@code V8__master_outbox_v2.sql}. The v1
 * {@code OutboxWriter}/{@code OutboxPublisher} beans it still registers are no
 * longer referenced or scheduled by master-service.
 */
@Configuration
public class OutboxConfig {

    /** UTC clock for the outbox publisher's lag metric + mark-published timestamps. */
    @Bean
    Clock outboxClock() {
        return Clock.systemUTC();
    }

    /**
     * Programmatic transaction boundary for the {@code AbstractOutboxPublisher}
     * poll loop (it reads pending rows and marks them published in separate
     * transactions on a background scheduler thread).
     */
    @Bean
    TransactionTemplate outboxTransactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    EventEnvelopeSerializer eventEnvelopeSerializer(ObjectMapper objectMapper) {
        return new EventEnvelopeSerializer(objectMapper);
    }

    @Bean
    DomainEventPort domainEventPort(MasterOutboxRepository outboxRepository,
                                    EventEnvelopeSerializer envelopeSerializer) {
        return new OutboxDomainEventAdapter(outboxRepository, envelopeSerializer);
    }
}
