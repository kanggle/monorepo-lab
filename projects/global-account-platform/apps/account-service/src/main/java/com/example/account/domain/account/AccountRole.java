package com.example.account.domain.account;

import com.example.account.domain.tenant.TenantId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Value object representing a tenant-scoped role assignment for an account.
 *
 * <p>TASK-BE-231: Roles are stored as simple strings. Full role-catalog validation
 * (TenantRoleCatalog) is deferred to a later task. The provisioning API accepts
 * any string[] and persists them directly.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRole {

    private Long id;
    private TenantId tenantId;
    private String accountId;
    private String roleName;
    private Instant assignedAt;

    public static AccountRole create(TenantId tenantId, String accountId, String roleName) {
        AccountRole role = new AccountRole();
        role.tenantId = tenantId;
        role.accountId = accountId;
        role.roleName = roleName;
        role.assignedAt = Instant.now();
        return role;
    }

    public static AccountRole reconstitute(Long id, TenantId tenantId, String accountId,
                                            String roleName, Instant assignedAt) {
        AccountRole role = new AccountRole();
        role.id = id;
        role.tenantId = tenantId;
        role.accountId = accountId;
        role.roleName = roleName;
        role.assignedAt = assignedAt;
        return role;
    }
}
