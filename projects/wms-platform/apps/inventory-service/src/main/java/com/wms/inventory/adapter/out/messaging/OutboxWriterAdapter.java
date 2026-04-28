package com.wms.inventory.adapter.out.messaging;

import com.wms.inventory.adapter.out.persistence.outbox.InventoryOutboxJpaEntity;
import com.wms.inventory.adapter.out.persistence.outbox.InventoryOutboxJpaRepository;
import com.wms.inventory.application.port.out.OutboxWriter;
import com.wms.inventory.domain.event.InventoryDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one outbox row per domain event using the caller's enclosing
 * {@code @Transactional} boundary ({@link Propagation#MANDATORY}).
 */
@Component
public class OutboxWriterAdapter implements OutboxWriter {

    private final InventoryOutboxJpaRepository repository;
    private final InventoryEventEnvelopeSerializer serializer;

    public OutboxWriterAdapter(InventoryOutboxJpaRepository repository,
                               InventoryEventEnvelopeSerializer serializer) {
        this.repository = repository;
        this.serializer = serializer;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void write(InventoryDomainEvent event) {
        InventoryEventEnvelopeSerializer.Serialised serialised = serializer.serialise(event);
        InventoryOutboxJpaEntity row = new InventoryOutboxJpaEntity(
                serialised.eventId(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                serialised.eventVersion(),
                serialised.json(),
                event.partitionKey(),
                event.occurredAt());
        repository.save(row);
    }
}
