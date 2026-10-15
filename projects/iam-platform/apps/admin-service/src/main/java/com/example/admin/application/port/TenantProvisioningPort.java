package com.example.admin.application.port;

import com.example.admin.application.tenant.TenantPageSummary;
import com.example.admin.application.tenant.TenantSummary;

/**
 * TASK-BE-250: Port for tenant lifecycle operations.
 * Implemented by {@code AccountServiceTenantClient} (infrastructure/client).
 *
 * <p>admin-service is NOT the source of truth for tenant data — all reads/writes
 * flow through account-service's internal API. This port isolates use cases from
 * HTTP client details.
 */
public interface TenantProvisioningPort {

    /**
     * Creates a new tenant in account-service.
     *
     * @throws com.example.admin.application.exception.TenantAlreadyExistsException if duplicate
     * @throws com.example.admin.application.exception.DownstreamFailureException    on 5xx/CB open
     */
    default TenantSummary create(String tenantId, String displayName, String tenantType) {
        return create(tenantId, displayName, tenantType, null);
    }

    /**
     * TASK-MONO-773 S4 (admin-to-account.md § Tenant lifecycle — 773 변경분) — {@code initialStatus}:
     * {@code null} = omitted = account-service's default {@code ACTIVE} (the self-service path, byte-unchanged);
     * {@code "PENDING"} = the {@code SUPER_ADMIN} path's «관리자 대기».
     */
    TenantSummary create(String tenantId, String displayName, String tenantType, String initialStatus);

    /**
     * Updates displayName and/or status of an existing tenant.
     * Null values are ignored (no update to that field).
     *
     * @throws com.example.admin.application.exception.TenantNotFoundException    if not found
     * @throws com.example.admin.application.exception.DownstreamFailureException on 5xx/CB open
     */
    TenantSummary update(String tenantId, String displayName, String status);

    /**
     * Retrieves a single tenant by id.
     *
     * @throws com.example.admin.application.exception.TenantNotFoundException    if not found
     * @throws com.example.admin.application.exception.DownstreamFailureException on 5xx/CB open
     */
    TenantSummary get(String tenantId);

    /**
     * Returns a paginated list of tenants.
     * Null filter values mean "no filter".
     */
    TenantPageSummary list(String statusFilter, String tenantTypeFilter, int page, int size);
}
