package com.example.account.domain.repository;

import com.example.account.domain.account.Account;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;

import java.util.Optional;

/**
 * Port interface for account persistence.
 *
 * <p>All query methods require a {@link TenantId} as the first argument to enforce
 * row-level isolation at the call site. There are no single-argument lookup methods
 * that could cause cross-tenant data leaks.
 *
 * <p>Rule (specs/features/multi-tenancy.md § Repository level):
 * "All JPA repository methods must receive tenant_id as the first argument.
 * findById(id) without tenant_id is forbidden."
 *
 * <p><b>Documented exception (TASK-BE-602):</b> {@link #findByIdResolvingTenant(String)}. It is
 * the only method here without a {@link TenantId} argument; see its javadoc for the conditions
 * that make it safe and its two registered consumers, and do not add another method — or another
 * consumer — without recording it the same way (specs/features/multi-tenancy.md § 격리 회귀 방지).
 */
public interface AccountRepository {

    /**
     * Persist or update an account. The account must carry a non-null {@link TenantId}.
     */
    Account save(Account account);

    /**
     * Tenant-scoped lookup by account id. Returns empty when the id exists in a
     * different tenant — cross-tenant ids are never visible across tenant boundaries.
     */
    Optional<Account> findById(TenantId tenantId, String id);

    /**
     * TASK-BE-602 — the ONE documented exception to "no lookup without a tenant": the tenant is
     * the <b>output</b> of this lookup, not its input. It answers "which tenant does this account
     * live in, and what is its status" for the social-login callback, which knows the account id
     * but cannot know the account's tenant (the identity row and the initiating client both carry
     * the wrong tenant for pre-BE-507 accounts).
     *
     * <p>Why this does not weaken isolation: {@code accounts.id} is a globally unique primary key,
     * so the result is at most one row and cannot mix tenants; the caller receives that row's own
     * {@link Account#getTenantId()} instead of guessing one; and its consumers are internal,
     * workload-authenticated endpoints that return no PII. Do not use it from a path that
     * already has a tenant — use {@link #findById(TenantId, String)} there.
     *
     * <p>Registered consumers (and no others):
     * <ol>
     *   <li>TASK-BE-602 — {@code GET /internal/accounts/{id}/status-with-tenant} (social-login
     *       status read; the tenant is returned in the response).</li>
     *   <li>TASK-MONO-735 — {@code POST /internal/accounts/{id}/lock|unlock|delete}, <b>only</b>
     *       when {@code X-Tenant-Id} is absent, blank or {@code "*"} (a caller that names no
     *       tenant: header-less internal callers, SUPER_ADMIN platform scope). When the header
     *       names a concrete tenant those endpoints stay on {@link #findById(TenantId, String)},
     *       so cross-tenant is still a 404 — applying this lookup regardless of the header would
     *       dissolve that confinement.</li>
     * </ol>
     */
    Optional<Account> findByIdResolvingTenant(String id);

    /**
     * Tenant-scoped lookup by email address.
     */
    Optional<Account> findByEmail(TenantId tenantId, String email);

    /**
     * Tenant-scoped existence check by email address.
     */
    boolean existsByEmail(TenantId tenantId, String email);

    /**
     * TASK-BE-372 (ADR-MONO-034 U6 step 3b): resolve the account's central
     * identity_id (the registry from step 3a). Returns empty when the account
     * does not exist in this tenant OR has no identity yet (enumeration-safe;
     * the caller fail-softs). Read-only.
     */
    Optional<String> findIdentityId(TenantId tenantId, String accountId);

    /**
     * TASK-BE-381 (ADR-MONO-036 P1/P3, M1): born-unified WRITER — set the account's
     * central {@code identity_id} when not already set (idempotent, net-zero; never
     * overwrites an existing value — ADR-034 § 1.3). Native UPDATE under the hood
     * ({@code identity_id} stays unmapped on the entity — the merge-overwrite hazard).
     *
     * @return rows assigned (1 = set; 0 = already set, or no such row in tenant)
     */
    int assignIdentityId(TenantId tenantId, String accountId, String identityId);

    /**
     * TASK-BE-231: Tenant-scoped paginated account list with optional status filter.
     * Used by the internal provisioning API.
     *
     * @param tenantId the owning tenant (required)
     * @param status   optional filter; {@code null} means no status filter
     * @param page     zero-based page number
     * @param size     page size
     * @return page slice of accounts
     */
    PageResult<Account> findAllByTenantId(TenantId tenantId, AccountStatus status, int page, int size);
}
