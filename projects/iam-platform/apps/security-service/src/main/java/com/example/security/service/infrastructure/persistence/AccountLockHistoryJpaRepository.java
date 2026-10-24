package com.example.security.service.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * TASK-MONO-811 — {@code occurred_at} of one tenant's lock rows with {@code occurred_at ∈ [from, to)}
     * (half-open). One row per {@code account.locked} event, so lock → unlock → relock is two rows.
     * Only the timestamp is selected (no account id, reason or actor). Served by
     * {@code idx_account_lock_history_tenant_occurred} (V0012).
     */
    @Query("SELECT h.occurredAt FROM AccountLockHistoryJpaEntity h "
            + "WHERE h.tenantId = :tenantId AND h.occurredAt >= :from AND h.occurredAt < :to")
    List<Instant> findOccurredAtInWindow(@Param("tenantId") String tenantId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    /** TASK-MONO-811 — {@link #findOccurredAtInWindow} across every tenant ({@code '*'}). V0012 index. */
    @Query("SELECT h.occurredAt FROM AccountLockHistoryJpaEntity h "
            + "WHERE h.occurredAt >= :from AND h.occurredAt < :to")
    List<Instant> findOccurredAtInWindowAllTenants(@Param("from") Instant from,
                                                   @Param("to") Instant to);
}
