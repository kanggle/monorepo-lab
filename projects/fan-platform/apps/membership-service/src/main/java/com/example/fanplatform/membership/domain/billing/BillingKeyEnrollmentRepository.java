package com.example.fanplatform.membership.domain.billing;

import com.example.fanplatform.membership.domain.membership.MembershipTier;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link BillingKeyEnrollment}. The end-user-facing lookups
 * are tenant + account scoped (multi-tenant.md M2); {@link #findAllActive(int)} is
 * a cross-tenant background-job scan (the auto-renew scheduler is a system job,
 * like the expiry sweeper's {@code findExpirable}).
 */
public interface BillingKeyEnrollmentRepository {

    BillingKeyEnrollment save(BillingKeyEnrollment enrollment);

    /** The single ACTIVE enrollment for (account, tenant, tier), if any. */
    Optional<BillingKeyEnrollment> findActiveByAccountAndTier(
            String accountId, String tenantId, MembershipTier tier);

    /**
     * Immediately deactivate any ACTIVE enrollment for (account, tenant, tier) via a
     * direct bulk UPDATE (executed now, NOT deferred to flush). Used by the enroll
     * path so the old row is inactive in the DB <b>before</b> the new active row is
     * inserted — Hibernate flushes inserts before updates, so a managed-entity
     * deactivate would momentarily leave two active rows and trip the partial unique
     * index. Returns the number of rows deactivated.
     */
    int deactivateActive(String accountId, String tenantId, MembershipTier tier);

    /**
     * The auto-renewal candidate batch: every ACTIVE enrollment, oldest first,
     * capped at {@code limit}. Cross-tenant by design (system background job).
     */
    List<BillingKeyEnrollment> findAllActive(int limit);
}
