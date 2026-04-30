package com.example.security.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountLockHistoryJpaRepository extends JpaRepository<AccountLockHistoryJpaEntity, Long> {

    Optional<AccountLockHistoryJpaEntity> findByEventId(String eventId);

    /**
     * TASK-BE-248: tenant-scoped account history. Uses the V0008 leading
     * {@code (tenant_id, account_id, occurred_at DESC)} index.
     */
    List<AccountLockHistoryJpaEntity> findByTenantIdAndAccountIdOrderByOccurredAtDesc(
            String tenantId, String accountId);
}
