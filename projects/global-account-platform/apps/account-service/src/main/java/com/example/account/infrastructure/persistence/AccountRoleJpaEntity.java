package com.example.account.infrastructure.persistence;

import com.example.account.domain.account.AccountRole;
import com.example.account.domain.tenant.TenantId;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "account_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRoleJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    public static AccountRoleJpaEntity fromDomain(AccountRole role) {
        AccountRoleJpaEntity entity = new AccountRoleJpaEntity();
        entity.id = role.getId();
        entity.tenantId = role.getTenantId().value();
        entity.accountId = role.getAccountId();
        entity.roleName = role.getRoleName();
        entity.assignedAt = role.getAssignedAt();
        return entity;
    }

    public AccountRole toDomain() {
        return AccountRole.reconstitute(id, new TenantId(tenantId), accountId, roleName, assignedAt);
    }
}
