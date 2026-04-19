package com.wms.master.config;

import com.example.messaging.outbox.OutboxPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.master.adapter.out.messaging.EventEnvelopeSerializer;
import com.wms.master.adapter.out.messaging.MasterOutboxPollingScheduler;
import com.wms.master.adapter.out.messaging.OutboxDomainEventPortAdapter;
import com.wms.master.adapter.out.messaging.OutboxMetrics;
import com.wms.master.application.port.out.DomainEventPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class OutboxConfig {

    @Bean
    EventEnvelopeSerializer eventEnvelopeSerializer(ObjectMapper objectMapper) {
        return new EventEnvelopeSerializer(objectMapper);
    }

    @Bean
    DomainEventPort domainEventPort(OutboxWriter outboxWriter,
                                    EventEnvelopeSerializer envelopeSerializer) {
        return new OutboxDomainEventPortAdapter(outboxWriter, envelopeSerializer);
    }

    @Bean
    OutboxMetrics outboxMetrics(MeterRegistry meterRegistry) {
        return new OutboxMetrics(meterRegistry);
    }

    @Bean
    @Profile("!standalone")
    MasterOutboxPollingScheduler masterOutboxPollingScheduler(
            OutboxPublisher outboxPublisher,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxMetrics outboxMetrics) {
        return new MasterOutboxPollingScheduler(outboxPublisher, kafkaTemplate, outboxMetrics);
    }
}
