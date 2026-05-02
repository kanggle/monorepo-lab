package com.example.account.application.service;

import com.example.account.application.exception.TenantAlreadyExistsException;
import com.example.account.application.exception.TenantNotFoundException;
import com.example.account.application.result.TenantResult;
import com.example.account.application.result.TenantPageResult;
import com.example.account.domain.repository.TenantRepository;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * TASK-BE-250: Tenant lifecycle CRUD use cases exposed via the internal provisioning
 * API (called from admin-service).
 *
 * <p>This service is account-service's authoritative write path for tenant data.
 * All creation/update operations are initiated by admin-service SUPER_ADMIN operators;
 * account-service validates business invariants and persists the canonical state.
 */
@Service
@RequiredArgsConstructor
public class TenantProvisionUseCase {

    private final TenantRepository tenantRepository;

    /**
     * Creates a new tenant. Status is always ACTIVE at creation time.
     *
     * @throws TenantAlreadyExistsException if a tenant with this id already exists
     * @throws IllegalArgumentException     if tenantId format is invalid
     */
    @Transactional
    public TenantResult create(String tenantId, String displayName, String tenantType) {
        TenantId id = new TenantId(tenantId); // validates format
        if (tenantRepository.existsById(id)) {
            throw new TenantAlreadyExistsException(tenantId);
        }
        TenantType type = TenantType.valueOf(tenantType);
        Instant now = Instant.now();
        Tenant tenant = Tenant.create(id, displayName.trim(), type, now);
        Tenant saved = tenantRepository.save(tenant);
        return TenantResult.from(saved);
    }

    /**
     * Updates an existing tenant's displayName and/or status.
     * At least one of the two parameters must be non-null.
     * Same-status update is a no-op for the status field.
     *
     * @throws TenantNotFoundException if no tenant with this id exists
     */
    @Transactional
    public TenantResult update(String tenantId, String displayName, String status) {
        TenantId id = new TenantId(tenantId);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        Instant now = Instant.now();
        if (displayName != null && !displayName.isBlank()) {
            tenant.updateDisplayName(displayName, now);
        }
        if (status != null && !status.isBlank()) {
            TenantStatus newStatus = TenantStatus.valueOf(status);
            tenant.updateStatus(newStatus, now);
        }

        Tenant saved = tenantRepository.save(tenant);
        return TenantResult.from(saved);
    }

    /**
     * Retrieves a single tenant by id.
     *
     * @throws TenantNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public TenantResult get(String tenantId) {
        TenantId id = new TenantId(tenantId);
        Tenant tenant = tenantRepository.findById(id)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return TenantResult.from(tenant);
    }

    /**
     * Returns a paginated list of tenants, optionally filtered by status and/or tenantType.
     */
    @Transactional(readOnly = true)
    public TenantPageResult list(String statusFilter, String tenantTypeFilter, int page, int size) {
        TenantStatus status = statusFilter != null ? TenantStatus.valueOf(statusFilter) : null;
        TenantType tenantType = tenantTypeFilter != null ? TenantType.valueOf(tenantTypeFilter) : null;
        Page<Tenant> tenantPage = tenantRepository.findAll(status, tenantType, PageRequest.of(page, size));
        return TenantPageResult.from(tenantPage);
    }
}
