package com.example.account.application.result;

import java.util.List;

/**
 * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07, rider P2): what moving a tenant between
 * org-nodes does to its <b>effective</b> entitled domains.
 *
 * <p>{@code domainsBefore = ACTIVE ∩ effectiveCeiling(from)} and
 * {@code domainsAfter = ACTIVE ∩ effectiveCeiling(to)}; a {@code null} node is ungrouped ⟹
 * UNBOUNDED (D7). The subscription rows themselves are never touched — the ceiling is
 * deny-only, so whatever a move takes away comes back when the tenant is moved out again.
 *
 * @param changed {@code false} for a preview, and for a write whose target equalled the
 *                current placement (idempotent no-op)
 */
public record TenantPlacementResult(
        String tenantId,
        String fromOrgNodeId,
        String toOrgNodeId,
        List<String> domainsBefore,
        List<String> domainsAfter,
        List<String> lostDomains,
        List<String> gainedDomains,
        boolean changed
) {}
