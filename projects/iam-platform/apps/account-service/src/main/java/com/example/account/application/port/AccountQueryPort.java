package com.example.account.application.port;

import com.example.account.application.result.AccountDetailResult;
import com.example.account.application.result.AccountSearchResult;
import com.example.account.domain.status.AccountStatus;

import java.util.List;
import java.util.Optional;

/**
 * Port for account search/query operations that require pagination or join queries
 * not supported by the domain repository interfaces.
 *
 * <p>Implemented by infrastructure layer ({@code AccountQueryPortImpl}).</p>
 *
 * <p>TASK-BE-357: search/list are tenant-scoped. {@code tenantId} is the concrete
 * tenant the (already effective-scope-gated) admin-service resolved; account-service
 * filters strictly by it. The platform sentinel {@code "*"} widens to all tenants
 * (reachable only by a SUPER_ADMIN whose gate admin-service has already enforced).
 */
public interface AccountQueryPort {

    /**
     * Tenant-scoped paginated account listing.
     *
     * @param tenantId concrete tenant; {@code "*"} → all tenants (platform scope)
     * @param status   TASK-BE-475: optional lifecycle-status filter; {@code null} → all statuses
     * @param page     zero-based page number
     * @param size     page size
     */
    AccountSearchResult findAll(String tenantId, AccountStatus status, int page, int size);

    /**
     * Exact email lookup within a tenant (the {@code (tenant_id, email)} unique
     * index — NOT a partial/LIKE search).
     *
     * @param tenantId concrete tenant → 0 or 1 row; {@code "*"} → all tenants
     *                 (the same email may exist under multiple tenants → 0..N rows)
     * @return matching items (possibly empty, never null)
     */
    List<AccountSearchResult.Item> findByEmail(String tenantId, String email);

    /**
     * TASK-BE-614 (multi-tenancy.md § 소비자 계정 풀 § 5): {@link #findAll} for a <b>site</b> tenant,
     * widened to the pool accounts holding an ACTIVE membership of that site. {@code "*"} keeps the
     * all-tenant meaning (pool accounts are already part of "all").
     */
    AccountSearchResult findAllIncludingPoolMembers(String siteTenantId, AccountStatus status, int page, int size);

    /** TASK-BE-614: {@link #findByEmail} widened the same way. */
    List<AccountSearchResult.Item> findByEmailIncludingPoolMembers(String siteTenantId, String email);

    Optional<AccountDetailResult> findDetailById(String accountId);
}
