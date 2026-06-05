package com.example.account.infrastructure.persistence;

import com.example.account.domain.account.AccountRole;
import com.example.account.domain.tenant.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA mapping for {@code account_roles}.
 *
 * <p>TASK-BE-255: Composite natural primary key
 * {@code (tenant_id, account_id, role_name)}; surrogate {@code id} column
 * removed in V0013. Attribution columns {@code granted_by} (nullable) and
 * {@code granted_at} are populated by the application layer.
 */
@Entity
@Table(name = "account_roles")
@IdClass(AccountRoleJpaEntity.AccountRoleId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountRoleJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Id
    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    @Id
    @Column(name = "role_name", nullable = false, length = 64)
    private String roleName;

    @Column(name = "granted_by", length = 36)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public static AccountRoleJpaEntity fromDomain(AccountRole role) {
        AccountRoleJpaEntity entity = new AccountRoleJpaEntity();
        entity.tenantId = role.getTenantId().value();
        entity.accountId = role.getAccountId();
        entity.roleName = role.getRoleName();
        entity.grantedBy = role.getGrantedBy();
        entity.grantedAt = role.getGrantedAt();
        return entity;
    }

    public AccountRole toDomain() {
        return AccountRole.reconstitute(
                new TenantId(tenantId), accountId, roleName, grantedBy, grantedAt);
    }

    /**
     * Composite primary key class required by {@link IdClass}. Mirrors the
     * three {@code @Id} columns above and supplies value-based equality.
     */
    public static class AccountRoleId implements Serializable {

        private String tenantId;
        private String accountId;
        private String roleName;

        public AccountRoleId() {}

        public AccountRoleId(String tenantId, String accountId, String roleName) {
            this.tenantId = tenantId;
            this.accountId = accountId;
            this.roleName = roleName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AccountRoleId other)) return false;
            return Objects.equals(tenantId, other.tenantId)
                    && Objects.equals(accountId, other.accountId)
                    && Objects.equals(roleName, other.roleName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, accountId, roleName);
        }
    }
}
