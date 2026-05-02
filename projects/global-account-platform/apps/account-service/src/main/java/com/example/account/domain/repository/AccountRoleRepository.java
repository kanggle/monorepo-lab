package com.example.account.domain.repository;

import com.example.account.domain.account.AccountRole;
import com.example.account.domain.tenant.TenantId;

import java.util.List;

/**
 * Port interface for account role persistence.
 *
 * <p>All methods require a {@link TenantId} to enforce row-level isolation.
 *
 * <p>TASK-BE-231: introduced as simple-string role storage.
 *
 * <p>TASK-BE-255: extended with {@link #addIfAbsent} and {@link #removeIfPresent}
 * for the single-role provisioning operations. Both are idempotent — re-adding
 * an existing role or removing an absent one is a no-op (returns {@code false}).
 */
public interface AccountRoleRepository {

    /** Persist a single role assignment. Used by the legacy bulk-replace path. */
    AccountRole save(AccountRole role);

    /** Return all roles for an account within the given tenant. */
    List<AccountRole> findByTenantIdAndAccountId(TenantId tenantId, String accountId);

    /** Delete all roles for an account within the given tenant. Used by replaceAll. */
    void deleteAllByTenantIdAndAccountId(TenantId tenantId, String accountId);

    /**
     * TASK-BE-255: Idempotently add a role to an account.
     *
     * @return {@code true} if a new row was inserted, {@code false} if the role
     *         was already present (no-op).
     */
    boolean addIfAbsent(AccountRole role);

    /**
     * TASK-BE-255: Idempotently remove a role from an account.
     *
     * @return {@code true} if a row was removed, {@code false} if the role
     *         was not currently assigned (no-op).
     */
    boolean removeIfPresent(TenantId tenantId, String accountId, String roleName);
}
