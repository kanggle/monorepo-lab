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
     * TASK-BE-614: every tenant of the given type, any status, ascending by id — unpaginated
     * because the caller must see <b>all</b> of them (the pool-signup refusal asks "does this
     * email have an account on any consumer site", and a missed page would be a silent hole).
     */
    List<Tenant> findAllByTenantType(TenantType tenantType);

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

    /**
     * TASK-BE-625 (ADR-MONO-047 § 개정 2026-10-07): the tenant row, write-locked until the
     * surrounding transaction ends. The placement write compares the current org-node with
     * the one admin-service authorized against and only then writes — without the lock two
     * concurrent placements could both pass that comparison and the second would move the
     * tenant out of a node nobody authorized against.
     */
    Optional<Tenant> findByIdForUpdate(TenantId tenantId);
}
