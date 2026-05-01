package com.wms.inbound.adapter.out.messaging;

import com.wms.inbound.adapter.out.persistence.outbox.InboundOutboxJpaEntity;
import com.wms.inbound.adapter.out.persistence.outbox.InboundOutboxJpaRepository;
import com.wms.inbound.application.port.out.InboundEventPort;
import com.wms.inbound.domain.event.InboundDomainEvent;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxWriterAdapter implements InboundEventPort {

    private final InboundOutboxJpaRepository repository;
    private final InboundEventEnvelopeSerializer serializer;
    private final Clock clock;

    public OutboxWriterAdapter(InboundOutboxJpaRepository repository,
                                InboundEventEnvelopeSerializer serializer,
                                Clock clock) {
        this.repository = repository;
        this.serializer = serializer;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(InboundDomainEvent event) {
        InboundEventEnvelopeSerializer.Serialised serialised = serializer.serialise(event);
        InboundOutboxJpaEntity row = new InboundOutboxJpaEntity(
                serialised.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                serialised.eventVersion(),
                serialised.json(),
                event.partitionKey(),
                clock.instant());
        repository.save(row);
    }
}
