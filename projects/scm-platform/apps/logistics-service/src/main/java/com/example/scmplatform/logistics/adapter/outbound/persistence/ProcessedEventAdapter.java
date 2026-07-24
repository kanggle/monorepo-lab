package com.example.scmplatform.logistics.adapter.outbound.persistence;

import com.example.scmplatform.logistics.application.port.outbound.ProcessedEventPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA-backed {@link ProcessedEventPort} (T8). SCAFFOLD — the seam consumer that calls it is
 * wired in TASK-SCM-BE-044.
 */
@Component
public class ProcessedEventAdapter implements ProcessedEventPort {

    private final ProcessedEventJpaRepository repository;

    ProcessedEventAdapter(ProcessedEventJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean isDuplicate(UUID eventId) {
        return repository.existsByEventId(eventId);
    }

    @Override
    public void markProcessed(UUID eventId, String tenantId, Instant processedAt, String sourceTopic) {
        try {
            ProcessedEventJpaEntity entity = new ProcessedEventJpaEntity();
            entity.setEventId(eventId);
            entity.setTenantId(tenantId);
            entity.setProcessedAt(processedAt);
            entity.setSourceTopic(sourceTopic);
            repository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // Concurrent duplicate insert — idempotent, ignore.
        }
    }
}
