package com.example.admin.application.port;

import java.util.Collection;
import java.util.Set;

/**
 * TASK-MONO-771 (ADR-MONO-080 D4 · R2) — read port over {@code tenant_entry_policy}
 * (data-model.md § {@code tenant_entry_policy}): «entering this tenant as an operator requires a second
 * factor». Keeps the JPA types out of the application layer.
 *
 * <p><b>Row absent ⟺ off.</b> Only a row with {@code require_mfa = TRUE} counts. A read failure is NOT
 * «off» — the implementation lets it propagate and the caller fails closed (data-model.md invariant).
 *
 * <p>This is the ENTRY-DECISION read (token exchange · assume-tenant, S4) and deliberately stays a single
 * method. The management surface's read/write (admin-api.md § Tenant Entry Policy, S5) is the separate
 * {@link TenantEntryPolicyManagementPort} — the gate never needs a write handle.
 */
public interface TenantEntryPolicyPort {

    /**
     * @param tenantIds the tenants to look at; {@code null}/blank/{@code '*'} entries are ignored
     *                  ({@code '*'} cannot hold a policy — {@code CHECK (tenant_id <> '*')})
     * @return the subset of {@code tenantIds} whose entry policy is ON ({@code require_mfa = TRUE});
     *         empty when none (never {@code null})
     */
    Set<String> findTenantsRequiringMfa(Collection<String> tenantIds);
}
