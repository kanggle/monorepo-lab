package com.example.scmplatform.demandplanning.adapter.outbound.persistence.adapter;

import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ProcessedEventJpaEntity;
import com.example.scmplatform.demandplanning.adapter.outbound.persistence.jpa.ProcessedEventJpaRepository;
import com.example.scmplatform.demandplanning.application.port.outbound.ProcessedEventPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProcessedEventAdapter implements ProcessedEventPort {

    private final ProcessedEventJpaRepository repository;

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
            // Concurrent duplicate insert — idempotent, ignore
        }
    }
}
