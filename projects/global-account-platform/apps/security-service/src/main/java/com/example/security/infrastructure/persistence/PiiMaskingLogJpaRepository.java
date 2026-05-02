package com.example.security.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for the PII masking idempotency log (TASK-BE-258).
 *
 * <p>Provides both idempotency-check (via the unique {@code event_id} constraint)
 * and bulk-masking UPDATE methods for the three security-service tables that
 * hold PII: {@code login_history}, {@code suspicious_events},
 * {@code account_lock_history}.
 *
 * <p>UPDATE clauses are tenant-scoped with {@code (tenant_id, account_id)} to
 * prevent cross-tenant data corruption. A zero-row UPDATE is treated as
 * success (account never interacted with this service).
 *
 * <p>Masking strategy per task spec:
 * <ul>
 *   <li>{@code ip_masked} → {@code '0.0.0.0'} (anonymized sentinel)</li>
 *   <li>{@code user_agent_family} → {@code 'REDACTED'}</li>
 *   <li>{@code device_fingerprint} → SHA2(CONCAT(account_id, salt), 256) (TASK-BE-270:
 *       application-level fixed salt prevents pre-image attacks against the UUID space)</li>
 * </ul>
 */
public interface PiiMaskingLogJpaRepository extends JpaRepository<PiiMaskingLogJpaEntity, Long> {

    boolean existsByEventId(String eventId);

    // ─── login_history ────────────────────────────────────────────────────

    /**
     * Masks PII columns in {@code login_history} for the given tenant/account pair.
     * {@code device_fingerprint} is replaced with SHA-256 of {@code accountId || salt}
     * (deterministic, non-reversible — TASK-BE-270 added salt to defeat pre-image
     * attacks against the UUID space). {@code tenant_id} and {@code account_id}
     * are preserved for audit integrity.
     */
    @Modifying
    @Query(value = """
            UPDATE login_history
               SET ip_masked          = '0.0.0.0',
                   user_agent_family  = 'REDACTED',
                   device_fingerprint = SHA2(CONCAT(:accountId, :salt), 256)
             WHERE tenant_id  = :tenantId
               AND account_id = :accountId
            """, nativeQuery = true)
    int maskLoginHistory(@Param("tenantId") String tenantId,
                         @Param("accountId") String accountId,
                         @Param("salt") String salt);

    // ─── suspicious_events ────────────────────────────────────────────────

    /**
     * Masks PII inside the {@code evidence} JSON of {@code suspicious_events}.
     * The {@code evidence} column is cleared to an empty JSON object so that
     * IP/UA fragments embedded in freeform description text are removed.
     * {@code tenant_id} and {@code account_id} are preserved.
     */
    @Modifying
    @Query(value = """
            UPDATE suspicious_events
               SET evidence = '{}'
             WHERE tenant_id  = :tenantId
               AND account_id = :accountId
            """, nativeQuery = true)
    int maskSuspiciousEvents(@Param("tenantId") String tenantId,
                              @Param("accountId") String accountId);

    // ─── account_lock_history ─────────────────────────────────────────────

    /**
     * {@code account_lock_history} stores {@code reason} and {@code locked_by}.
     * Neither is PII by itself (reason is a code, locked_by is an operator UUID
     * or the system sentinel). No masking needed for this table — the row is
     * included in {@code tableNames} to satisfy the "which tables were checked"
     * audit requirement, but the UPDATE is a no-op to keep the contract explicit.
     *
     * <p>If future schema revisions add IP or UA columns, add masking here.
     */
    @Modifying
    @Query(value = """
            UPDATE account_lock_history
               SET account_id = account_id
             WHERE tenant_id  = :tenantId
               AND account_id = :accountId
            """, nativeQuery = true)
    int touchAccountLockHistory(@Param("tenantId") String tenantId,
                                 @Param("accountId") String accountId);
}
