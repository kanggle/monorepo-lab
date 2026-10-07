package com.example.admin.application.orgnode;

import java.util.List;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07, rider P2) — what a tenant placement does to the
 * tenant's effective entitled domains, as computed by the authority (account-service owns both
 * the subscriptions and the ceilings).
 *
 * <p>{@code domainsBefore = ACTIVE ∩ effectiveCeiling(from)}, {@code domainsAfter =
 * ACTIVE ∩ effectiveCeiling(to)}. The subscription rows are not touched — the ceiling is
 * deny-only, so a detach restores whatever an attach took away.
 *
 * @param changed {@code false} for a preview and for an idempotent no-op write
 */
public record TenantPlacementView(
        String tenantId,
        String fromOrgNodeId,
        String toOrgNodeId,
        List<String> domainsBefore,
        List<String> domainsAfter,
        List<String> lostDomains,
        List<String> gainedDomains,
        boolean changed
) {}
