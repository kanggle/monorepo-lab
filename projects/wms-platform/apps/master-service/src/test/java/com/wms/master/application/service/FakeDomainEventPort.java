package com.wms.master.application.service;

import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.domain.event.DomainEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * In-memory fake for {@link DomainEventPort}. Accumulates every publish call
 * so tests can assert ordering, counts, and content.
 */
class FakeDomainEventPort implements DomainEventPort {

    private final List<DomainEvent> published = new ArrayList<>();

    @Override
    public void publish(List<DomainEvent> events) {
        published.addAll(events);
    }

    List<DomainEvent> published() {
        return List.copyOf(published);
    }

    @SuppressWarnings("unchecked")
    <T extends DomainEvent> T single(Class<T> type) {
        if (published.size() != 1) {
            throw new AssertionError("Expected exactly 1 event, got " + published.size());
        }
        DomainEvent event = published.get(0);
        if (!type.isInstance(event)) {
            throw new AssertionError(
                    "Expected event of type " + type.getSimpleName()
                            + ", got " + event.getClass().getSimpleName());
        }
        return (T) event;
    }

    void clear() {
        published.clear();
    }
}
