package com.example.account.domain.repository;

import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * Port interface for tenant persistence.
 *
 * <p>Implemented by {@code TenantJpaAdapter} in the infrastructure layer.
 * The application layer depends only on this interface — never on JPA specifics.
 */
public interface TenantRepository {

    Optional<Tenant> findById(TenantId tenantId);

    /**
     * Returns {@code true} when a tenant with the given id exists and its status is ACTIVE.
     * Used for pre-condition checks before provisioning accounts into a tenant.
     */
    boolean existsActive(TenantId tenantId);

    /** Returns {@code true} when any tenant with the given id exists (regardless of status). */
    boolean existsById(TenantId tenantId);

    /**
     * Persists a new or updated Tenant. Used by admin-service internal provisioning
     * endpoints (TASK-BE-250).
     */
    Tenant save(Tenant tenant);

    /**
     * Paginated listing with optional status and tenantType filters.
     * Null filter values are treated as "no filter" (all values accepted).
     */
    Page<Tenant> findAll(TenantStatus statusFilter, TenantType tenantTypeFilter, Pageable pageable);
}
