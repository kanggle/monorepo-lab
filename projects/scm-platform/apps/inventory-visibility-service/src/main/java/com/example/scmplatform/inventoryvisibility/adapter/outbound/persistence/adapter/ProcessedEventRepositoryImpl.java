package com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.adapter;

import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.ProcessedEventJpaEntity;
import com.example.scmplatform.inventoryvisibility.adapter.outbound.persistence.jpa.ProcessedEventJpaRepository;
import com.example.scmplatform.inventoryvisibility.application.port.outbound.ProcessedEventPort;
import com.example.scmplatform.inventoryvisibility.domain.dedupe.ProcessedEventRecord;
import com.example.scmplatform.inventoryvisibility.domain.dedupe.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProcessedEventRepositoryImpl implements ProcessedEventRepository, ProcessedEventPort {

    private final ProcessedEventJpaRepository jpaRepository;

    @Override
    public boolean existsByEventId(UUID eventId) {
        return jpaRepository.existsByEventId(eventId.toString());
    }

    @Override
    public Optional<ProcessedEventRecord> findByEventId(UUID eventId) {
        return jpaRepository.findById(eventId.toString()).map(this::toDomain);
    }

    @Override
    public ProcessedEventRecord save(ProcessedEventRecord record) {
        ProcessedEventJpaEntity e = new ProcessedEventJpaEntity();
        e.setEventId(record.getEventId().toString());
        e.setTenantId(record.getTenantId());
        e.setProcessedAt(record.getProcessedAt());
        e.setSourceTopic(record.getSourceTopic());
        return toDomain(jpaRepository.save(e));
    }

    // ProcessedEventPort implementation
    @Override
    public boolean isDuplicate(UUID eventId) {
        return existsByEventId(eventId);
    }

    @Override
    public void markProcessed(UUID eventId, String tenantId, Instant processedAt, String sourceTopic) {
        save(ProcessedEventRecord.of(eventId, tenantId, processedAt, sourceTopic));
    }

    private ProcessedEventRecord toDomain(ProcessedEventJpaEntity e) {
        return ProcessedEventRecord.of(
                ReadModelIds.requireUuid(e.getEventId(), "event_dedupe.event_id"),
                e.getTenantId(),
                e.getProcessedAt(),
                e.getSourceTopic()
        );
    }
}
