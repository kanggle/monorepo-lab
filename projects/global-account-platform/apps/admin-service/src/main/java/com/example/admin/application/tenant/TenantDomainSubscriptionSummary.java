package com.example.admin.application.tenant;

/**
 * TASK-BE-322 (ADR-MONO-019 D4): domain result record for a single ACTIVE
 * tenant↔domain subscription read from account-service. Free of HTTP/framework
 * types.
 */
public record TenantDomainSubscriptionSummary(
        String tenantId,
        String domainKey
) {}
