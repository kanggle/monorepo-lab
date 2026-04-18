package com.wms.master.application.port.out;

import com.wms.master.domain.event.DomainEvent;
import java.util.List;

/**
 * Outbound port for publishing domain events. The real adapter writes events to
 * the transactional outbox (see libs/java-messaging), so callers must invoke
 * {@link #publish(List)} inside the same transaction as the state change.
 *
 * <p>In Phase 5 this port exists for wiring; the outbox adapter arrives in
 * Phase 7. Tests use an in-memory fake to assert publication behavior.
 */
public interface DomainEventPort {

    void publish(List<DomainEvent> events);
}
