package com.example.erp.notification.infrastructure.persistence.adapter;

import com.example.erp.notification.application.port.outbound.EventDedupeStore;
import com.example.erp.notification.domain.dedupe.EventDedupeRecord;
import com.example.erp.notification.infrastructure.persistence.jpa.ProcessedEventJpaEntity;
import com.example.erp.notification.infrastructure.persistence.jpa.ProcessedEventJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventDedupeStoreImpl implements EventDedupeStore {

    private final ProcessedEventJpaRepository jpa;

    @Override
    public boolean isProcessed(String eventId) {
        return jpa.existsById(eventId);
    }

    @Override
    public void markProcessed(EventDedupeRecord record) {
        ProcessedEventJpaEntity e = new ProcessedEventJpaEntity();
        e.setEventId(record.eventId());
        e.setTopic(record.topic());
        e.setAggregateId(record.aggregateId());
        e.setProcessedAt(record.processedAt());
        jpa.save(e);
    }
}
