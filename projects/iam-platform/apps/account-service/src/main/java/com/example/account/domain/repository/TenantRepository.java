package com.example.account.domain.repository;

import com.example.account.domain.orgnode.OrgNodeId;
import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;

import java.util.List;
import java.util.Optional;

/**
 * Port interface for tenant persistence.
 *
 * <p>Implemented by {@code TenantRepositoryImpl} in the infrastructure layer.
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
     *
     * @param page zero-based page number
     * @param size page size
     */
    PageResult<Tenant> findAll(TenantStatus statusFilter, TenantType tenantTypeFilter, int page, int size);

    /**
     * TASK-BE-491 (ADR-MONO-047 § D5): tenant ids attached to any of the given org-node ids,
     * ascending. Backs the subtree expansion admin-service uses to resolve an
     * {@code ORG_ADMIN @ node} grant. An empty input yields an empty list.
     */
    List<String> findTenantIdsByOrgNodeIdIn(List<String> orgNodeIds);

    /**
     * TASK-BE-491 (ADR-MONO-047 invariant I4): how many tenants are attached to this node.
     * A node with tenants may not be deleted — that would strand service-tenants.
     */
    long countByOrgNodeId(OrgNodeId orgNodeId);
}
