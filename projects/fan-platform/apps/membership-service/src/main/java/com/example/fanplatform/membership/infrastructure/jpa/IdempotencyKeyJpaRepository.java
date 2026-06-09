package com.example.fanplatform.membership.infrastructure.jpa;

import com.example.fanplatform.membership.domain.idempotency.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKey, IdempotencyKey.Pk> {

    Optional<IdempotencyKey> findByTenantIdAndAccountIdAndIdempotencyKey(
            String tenantId, String accountId, String idempotencyKey);
}
