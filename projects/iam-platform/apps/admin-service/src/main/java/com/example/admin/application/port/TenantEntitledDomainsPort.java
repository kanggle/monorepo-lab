package com.example.admin.application.port;

import java.util.Set;

/**
 * TASK-BE-631 (ADR-MONO-083 · admin-api.md § 도메인 업무 권한 세트 «배정 가능한 세트») — a tenant's EFFECTIVE entitled
 * domains = active subscriptions ∩ org-node ceiling, read from the entitlement authority (account-service
 * {@code GET /internal/tenants/{tenantId}/entitled-domains} — the one place the ceiling is applied,
 * account-tenant-domain-subscriptions.md). The upper bound of every set create / edit / grant.
 *
 * <p>Fail-closed: any failure throws {@link com.example.admin.application.exception.DownstreamFailureException}
 * (or the circuit-breaker's {@code CallNotPermittedException}) — 503, nothing written. An unknown tenant is
 * answered with the empty set (nothing is entitled there), so the caller refuses with 422.
 */
public interface TenantEntitledDomainsPort {

    /** @return the subscription domain keys the tenant is entitled to (never {@code null}). */
    Set<String> entitledDomains(String tenantId);
}
