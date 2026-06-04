package com.example.erp.readmodel.adapter.outbound.persistence.adapter;

import com.example.erp.readmodel.adapter.outbound.persistence.jpa.ProcessedEventJpaEntity;
import com.example.erp.readmodel.adapter.outbound.persistence.jpa.ProcessedEventJpaRepository;
import com.example.erp.readmodel.application.port.outbound.EventDedupeStore;
import com.example.erp.readmodel.domain.dedupe.EventDedupeRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class EventDedupeStoreImpl implements EventDedupeStore {

    private final ProcessedEventJpaRepository jpa;

    @Override
    public boolean isProcessed(String eventId) {
        return jpa.existsById(eventId);
    }

    @Override
    public Optional<EventDedupeRecord> find(String eventId) {
        return jpa.findById(eventId).map(this::toDomain);
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

    private EventDedupeRecord toDomain(ProcessedEventJpaEntity e) {
        return EventDedupeRecord.of(e.getEventId(), e.getTopic(), e.getAggregateId(),
                e.getProcessedAt());
    }
}
