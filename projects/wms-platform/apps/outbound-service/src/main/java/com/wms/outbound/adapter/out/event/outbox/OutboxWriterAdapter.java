package com.wms.outbound.adapter.out.event.outbox;

import com.wms.outbound.adapter.out.event.publisher.EventEnvelopeSerializer;
import com.wms.outbound.adapter.out.persistence.entity.OutboundOutboxEntity;
import com.wms.outbound.adapter.out.persistence.repository.OutboundOutboxRepository;
import com.wms.outbound.application.port.out.OutboxWriterPort;
import com.wms.outbound.domain.event.OutboundDomainEvent;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real {@link OutboxWriterPort} implementation. Replaces the
 * {@code StubOutboxWriterAdapter} from TASK-BE-034.
 *
 * <p>The outbox row is written under {@code Propagation.MANDATORY} so it
 * always joins the use-case's transaction (T3 outbox pattern).
 */
@Component
public class OutboxWriterAdapter implements OutboxWriterPort {

    private static final String STATUS_PENDING = "PENDING";

    private final OutboundOutboxRepository repository;
    private final EventEnvelopeSerializer serializer;
    private final Clock clock;

    public OutboxWriterAdapter(OutboundOutboxRepository repository,
                               EventEnvelopeSerializer serializer,
                               Clock clock) {
        this.repository = repository;
        this.serializer = serializer;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(OutboundDomainEvent event) {
        EventEnvelopeSerializer.Serialised serialised = serializer.serialise(event);
        OutboundOutboxEntity row = new OutboundOutboxEntity(
                serialised.eventId(),
                upperCase(event.aggregateType()),
                event.aggregateId(),
                event.eventType(),
                serialised.eventVersion(),
                serialised.json(),
                event.partitionKey(),
                STATUS_PENDING,
                clock.instant());
        repository.save(row);
    }

    private static String upperCase(String aggregateType) {
        return aggregateType == null ? null : aggregateType.toUpperCase();
    }
}
