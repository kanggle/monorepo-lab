package com.example.account.domain.account;

import com.example.account.domain.tenant.TenantId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Value object representing a tenant-scoped role assignment for an account.
 *
 * <p>TASK-BE-231: Roles were introduced as simple strings.
 *
 * <p>TASK-BE-255: The persisted shape gained a {@code grantedBy} attribution
 * column (operator id, nullable for system grants) and the timestamp column
 * was renamed from {@code assigned_at} to {@code granted_at} to match the
 * data-model.md vocabulary. The natural composite key is
 * {@code (tenantId, accountId, roleName)} — the row itself is the fact.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRole {

    private TenantId tenantId;
    private String accountId;
    private String roleName;
    private String grantedBy;
    private Instant grantedAt;

    /**
     * Create a new role assignment with attribution. {@code grantedBy} may be
     * {@code null} when the assignment is performed by a system / fallback path.
     */
    public static AccountRole create(TenantId tenantId, String accountId, String roleName,
                                      String grantedBy) {
        AccountRoleName.validate(roleName);
        AccountRole role = new AccountRole();
        role.tenantId = tenantId;
        role.accountId = accountId;
        role.roleName = roleName;
        role.grantedBy = grantedBy;
        role.grantedAt = Instant.now();
        return role;
    }

    /**
     * Reconstitute an {@code AccountRole} from persisted state. Used by
     * infrastructure mappers that already trust the stored values.
     */
    public static AccountRole reconstitute(TenantId tenantId, String accountId, String roleName,
                                            String grantedBy, Instant grantedAt) {
        AccountRole role = new AccountRole();
        role.tenantId = tenantId;
        role.accountId = accountId;
        role.roleName = roleName;
        role.grantedBy = grantedBy;
        role.grantedAt = grantedAt;
        return role;
    }
}
