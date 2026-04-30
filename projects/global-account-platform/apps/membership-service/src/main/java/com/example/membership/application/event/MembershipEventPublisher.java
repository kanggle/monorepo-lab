package com.example.membership.application.event;

import com.example.membership.domain.event.MembershipDomainEvent;
import com.example.membership.domain.subscription.Subscription;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class MembershipEventPublisher extends BaseEventPublisher {

    private static final String AGGREGATE_TYPE = "membership";
    private static final String SOURCE = "membership-service";

    public MembershipEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishActivated(Subscription s) {
        write(s.getAccountId(), s.buildActivatedEvent());
    }

    public void publishExpired(Subscription s) {
        write(s.getAccountId(), s.buildExpiredEvent());
    }

    public void publishCancelled(Subscription s) {
        write(s.getAccountId(), s.buildCancelledEvent());
    }

    private void write(String aggregateId, MembershipDomainEvent event) {
        writeEvent(AGGREGATE_TYPE, aggregateId, event.eventType(), SOURCE, event.payload());
    }
}
