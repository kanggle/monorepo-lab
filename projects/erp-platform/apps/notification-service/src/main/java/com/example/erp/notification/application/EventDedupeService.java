package com.example.erp.notification.application;

import com.example.erp.notification.application.port.outbound.ClockPort;
import com.example.erp.notification.application.port.outbound.EventDedupeStore;
import com.example.erp.notification.domain.dedupe.EventDedupeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Consumer idempotency service (T8). Wraps the {@link EventDedupeStore}: a
 * caller checks {@link #isDuplicate} before dispatching an event and records
 * {@link #markProcessed} after a successful dispatch, both inside the caller's
 * transaction. A duplicate {@code eventId} is skipped without mutation so
 * re-delivery leaves the inbox byte-identical.
 */
@Service
@RequiredArgsConstructor
public class EventDedupeService {

    private final EventDedupeStore store;
    private final ClockPort clock;

    public boolean isDuplicate(String eventId) {
        return store.isProcessed(eventId);
    }

    public void markProcessed(String eventId, String topic, String aggregateId) {
        store.markProcessed(EventDedupeRecord.of(eventId, topic, aggregateId, clock.now()));
    }
}
