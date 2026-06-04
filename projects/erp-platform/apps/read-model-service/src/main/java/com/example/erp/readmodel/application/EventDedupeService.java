package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.port.outbound.ClockPort;
import com.example.erp.readmodel.application.port.outbound.EventDedupeStore;
import com.example.erp.readmodel.domain.dedupe.EventDedupeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Consumer idempotency service (T8). Wraps the {@link EventDedupeStore}: a
 * caller checks {@link #isDuplicate} before applying an event and records
 * {@link #markProcessed} after a successful projection upsert, both inside the
 * caller's transaction. A duplicate {@code eventId} is skipped without mutation
 * so re-delivery leaves the projection byte-identical.
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
