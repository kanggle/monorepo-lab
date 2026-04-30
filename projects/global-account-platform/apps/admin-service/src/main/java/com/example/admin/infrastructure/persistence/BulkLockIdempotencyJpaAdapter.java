package com.example.admin.infrastructure.persistence;

import com.example.admin.application.port.BulkLockIdempotencyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * JPA-backed adapter for {@link BulkLockIdempotencyPort}. Translates the
 * generic port contract onto the {@link BulkLockIdempotencyJpaRepository} and
 * maps {@link DataIntegrityViolationException} (raised by PK collisions on
 * concurrent first requests) to {@link BulkLockIdempotencyPort.DuplicateKeyException}.
 */
@Component
@RequiredArgsConstructor
public class BulkLockIdempotencyJpaAdapter implements BulkLockIdempotencyPort {

    private final BulkLockIdempotencyJpaRepository repository;

    @Override
    public Optional<Record> find(Long operatorId, String idempotencyKey) {
        return repository.findById(new BulkLockIdempotencyJpaEntity.Key(operatorId, idempotencyKey))
                .map(e -> new Record(
                        e.getId().getOperatorId(),
                        e.getId().getIdempotencyKey(),
                        e.getRequestHash(),
                        e.getResponseBody(),
                        e.getCreatedAt()));
    }

    @Override
    public void save(Long operatorId, String idempotencyKey, String requestHash,
                     String responseBody, Instant createdAt) throws DuplicateKeyException {
        try {
            repository.saveAndFlush(BulkLockIdempotencyJpaEntity.create(
                    operatorId, idempotencyKey, requestHash, responseBody, createdAt));
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateKeyException(ex);
        }
    }
}
