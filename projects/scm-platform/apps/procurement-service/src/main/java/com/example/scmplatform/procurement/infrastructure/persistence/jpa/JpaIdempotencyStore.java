package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.scmplatform.procurement.application.port.outbound.IdempotencyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for {@link IdempotencyStore} (TASK-BE-445). {@code saveAndFlush}
 * surfaces a duplicate-PK insert (the concurrent first-request race) eagerly as a
 * {@code DataIntegrityViolationException} so the losing transaction rolls back its
 * side effects instead of committing a second execution.
 */
@Component
public class JpaIdempotencyStore implements IdempotencyStore {

    private final IdempotencyKeyJpaRepository repository;

    public JpaIdempotencyStore(IdempotencyKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<IdempotencyRecord> find(String tenantId, String endpoint, String key) {
        return repository.findById(new IdempotencyKeyJpaEntity.Id(key, endpoint, tenantId))
                .map(e -> new IdempotencyRecord(
                        e.getPayloadHash(), e.getResponseStatus(), e.getResponseBody()));
    }

    @Override
    public void save(String tenantId, String endpoint, String key,
                     String payloadHash, int responseStatus, String responseBody, Duration ttl) {
        Instant now = Instant.now();
        repository.saveAndFlush(new IdempotencyKeyJpaEntity(
                key, endpoint, tenantId,
                payloadHash, responseStatus, responseBody,
                now, now.plus(ttl)));
    }
}
