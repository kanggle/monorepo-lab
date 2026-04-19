package com.wms.master.adapter.out.messaging;

import com.example.messaging.outbox.OutboxWriter;
import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.domain.event.DomainEvent;
import java.util.List;

/**
 * {@link DomainEventPort} implementation that serializes each event into the
 * contract envelope and writes one outbox row per event. Invoked inside the
 * domain transaction so the state change and outbox row commit atomically.
 */
public class OutboxDomainEventPortAdapter implements DomainEventPort {

    private final OutboxWriter outboxWriter;
    private final EventEnvelopeSerializer envelopeSerializer;

    public OutboxDomainEventPortAdapter(OutboxWriter outboxWriter,
                                        EventEnvelopeSerializer envelopeSerializer) {
        this.outboxWriter = outboxWriter;
        this.envelopeSerializer = envelopeSerializer;
    }

    @Override
    public void publish(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            String payload = envelopeSerializer.serialize(event);
            outboxWriter.save(
                    event.aggregateType(),
                    event.aggregateId().toString(),
                    event.eventType(),
                    payload);
        }
    }
}
