package com.example.account.domain.repository;

import com.example.account.domain.account.AccountRole;
import com.example.account.domain.tenant.TenantId;

import java.util.List;

/**
 * Port interface for account role persistence.
 *
 * <p>All methods require a {@link TenantId} to enforce row-level isolation.
 * TASK-BE-231: roles are stored as simple strings; TenantRoleCatalog validation
 * is deferred to a later task.
 */
public interface AccountRoleRepository {

    /** Persist a single role assignment. */
    AccountRole save(AccountRole role);

    /** Return all roles for an account within the given tenant. */
    List<AccountRole> findByTenantIdAndAccountId(TenantId tenantId, String accountId);

    /** Delete all roles for an account within the given tenant. Used by AssignRolesUseCase. */
    void deleteAllByTenantIdAndAccountId(TenantId tenantId, String accountId);
}
