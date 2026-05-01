package com.example.security.domain;

/**
 * Tenant identifier constants.
 *
 * <p>TASK-BE-248 Phase 1 introduces {@code tenant_id} into the security-service
 * schema and domain entities, but the upstream events (auth.login.*,
 * account.locked, etc.) do not yet carry a {@code tenantId} field on the
 * payload — that wiring is Phase 2 work. Until then, all events handled by
 * security-service originate from the single B2C consumer tenant
 * {@link #DEFAULT_TENANT_ID} ({@code "fan-platform"}), which matches the
 * backfill strategy used by Flyway migration {@code V0008__add_tenant_id.sql}.
 *
 * <p>When Phase 2 lands, callers should read {@code tenantId} from the event
 * envelope (after publisher signatures are updated) and the in-process
 * fallback to this constant must be removed. References to this class are
 * intentionally easy to grep — every {@code Tenants.DEFAULT_TENANT_ID} usage
 * is a Phase 2 follow-up site.
 */
public final class Tenants {

    public static final String DEFAULT_TENANT_ID = "fan-platform";

    private Tenants() {
    }
}
