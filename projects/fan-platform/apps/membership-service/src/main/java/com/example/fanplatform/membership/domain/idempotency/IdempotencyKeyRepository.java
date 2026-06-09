package com.example.fanplatform.membership.domain.idempotency;

import java.util.Optional;

/**
 * Persistence port for subscribe idempotency records.
 */
public interface IdempotencyKeyRepository {

    Optional<IdempotencyKey> find(String tenantId, String accountId, String idempotencyKey);

    IdempotencyKey save(IdempotencyKey key);
}
