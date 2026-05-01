package com.wms.inbound.domain.event;

import java.time.Instant;
import java.util.UUID;

public sealed interface InboundDomainEvent
        permits AsnReceivedEvent, AsnCancelledEvent, InspectionCompletedEvent,
                PutawayInstructedEvent, PutawayCompletedEvent, AsnClosedEvent {

    UUID aggregateId();

    String aggregateType();

    String eventType();

    String partitionKey();

    Instant occurredAt();

    String actorId();
}
