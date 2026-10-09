package com.example.admin.application.port;

import java.time.Instant;
import java.util.Optional;

/**
 * TASK-MONO-771 S5 (ADR-MONO-080 D4, owner decision OD-1) — the management surface's port over
 * {@code tenant_entry_policy} (admin-api.md § Tenant Entry Policy): one tenant's row, read and full-replace
 * write. Separate from the entry-decision read {@link TenantEntryPolicyPort} — both are implemented by the
 * same JPA adapter, so what this port writes is exactly what that port reads.
 */
public interface TenantEntryPolicyManagementPort {

    /** One tenant's policy row, or empty when it has never been set (= off). A read failure propagates. */
    Optional<EntryPolicyView> find(String tenantId);

    /**
     * Full-replace write of one tenant's policy: inserts the row when absent, otherwise updates it under
     * the row's optimistic {@code version}. Turning the policy off keeps the row with
     * {@code require_mfa = FALSE} (last changer / time preserved — admin-api.md).
     *
     * <p>A concurrent writer surfaces as Spring's {@code ObjectOptimisticLockingFailureException}
     * (→ 409 {@code OPTIMISTIC_LOCK_CONFLICT}), including two racing first inserts.
     *
     * @param tenantId            never {@code '*'} (the caller validates; the DB CHECK is the backstop)
     * @param requireMfa          the new value
     * @param updatedByInternalId the writer's {@code admin_operators.id}, or {@code null}
     * @param at                  the write time
     * @return the row as written
     */
    EntryPolicyView save(String tenantId, boolean requireMfa, Long updatedByInternalId, Instant at);

    /**
     * One tenant's entry policy. {@code updatedByOperatorId} is the writer's external UUID
     * ({@code admin_operators.operator_id}) — the internal BIGINT PK is never exposed; {@code null} when
     * never written or when the writer row is gone ({@code ON DELETE SET NULL}).
     */
    record EntryPolicyView(String tenantId, boolean requireMfa, Instant updatedAt, String updatedByOperatorId) {}
}
