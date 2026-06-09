package com.example.fanplatform.membership.infrastructure.jpa;

import com.example.fanplatform.membership.domain.idempotency.IdempotencyKey;
import com.example.fanplatform.membership.domain.idempotency.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IdempotencyKeyRepositoryImpl implements IdempotencyKeyRepository {

    private final IdempotencyKeyJpaRepository jpa;

    @Override
    public Optional<IdempotencyKey> find(String tenantId, String accountId, String idempotencyKey) {
        return jpa.findByTenantIdAndAccountIdAndIdempotencyKey(tenantId, accountId, idempotencyKey);
    }

    @Override
    public IdempotencyKey save(IdempotencyKey key) {
        return jpa.save(key);
    }
}
