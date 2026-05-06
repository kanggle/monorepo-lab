package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.adapter;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.EventDedupeJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.EventDedupeJpaRepository;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.EventDedupePort;
import com.example.scmplatform.inventoryvisibility.domain.dedupe.EventDedupeRecord;
import com.example.scmplatform.inventoryvisibility.domain.dedupe.repository.EventDedupeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventDedupeRepositoryAdapter implements EventDedupeRepository, EventDedupePort {

    private final EventDedupeJpaRepository jpaRepository;

    @Override
    public boolean existsByEventId(UUID eventId) {
        return jpaRepository.existsByEventId(eventId.toString());
    }

    @Override
    public Optional<EventDedupeRecord> findByEventId(UUID eventId) {
        return jpaRepository.findById(eventId.toString()).map(this::toDomain);
    }

    @Override
    public EventDedupeRecord save(EventDedupeRecord record) {
        EventDedupeJpaEntity e = new EventDedupeJpaEntity();
        e.setEventId(record.getEventId().toString());
        e.setTenantId(record.getTenantId());
        e.setProcessedAt(record.getProcessedAt());
        e.setSourceTopic(record.getSourceTopic());
        return toDomain(jpaRepository.save(e));
    }

    // EventDedupePort implementation
    @Override
    public boolean isDuplicate(UUID eventId) {
        return existsByEventId(eventId);
    }

    @Override
    public void markProcessed(UUID eventId, String tenantId, Instant processedAt, String sourceTopic) {
        save(EventDedupeRecord.of(eventId, tenantId, processedAt, sourceTopic));
    }

    private EventDedupeRecord toDomain(EventDedupeJpaEntity e) {
        return EventDedupeRecord.of(
                UUID.fromString(e.getEventId()),
                e.getTenantId(),
                e.getProcessedAt(),
                e.getSourceTopic()
        );
    }
}
