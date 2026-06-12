package com.example.finance.ledger.infrastructure.persistence.jpa;

import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** JPA adapter for {@link ProcessedEventStore} (F1 dedupe). */
@Component
@RequiredArgsConstructor
public class ProcessedEventStoreImpl implements ProcessedEventStore {

    private final ProcessedEventJpaRepository jpa;

    @Override
    public boolean isProcessed(String eventId) {
        return jpa.existsById(eventId);
    }

    @Override
    public void markProcessed(String eventId, String tenantId, String topic,
                              String sourceTransactionId, Instant processedAt) {
        ProcessedEventJpaEntity e = new ProcessedEventJpaEntity();
        e.setEventId(eventId);
        e.setTenantId(tenantId);
        e.setTopic(topic);
        e.setSourceTransactionId(sourceTransactionId);
        e.setProcessedAt(processedAt);
        jpa.save(e);
    }
}
