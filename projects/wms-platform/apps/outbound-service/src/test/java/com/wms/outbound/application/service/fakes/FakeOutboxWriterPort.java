package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.OutboxWriterPort;
import com.wms.outbound.domain.event.OutboundDomainEvent;
import java.util.ArrayList;
import java.util.List;

public class FakeOutboxWriterPort implements OutboxWriterPort {

    public final List<OutboundDomainEvent> published = new ArrayList<>();

    @Override
    public void publish(OutboundDomainEvent event) {
        published.add(event);
    }

    public int countByType(String eventType) {
        return (int) published.stream().filter(e -> e.eventType().equals(eventType)).count();
    }
}
