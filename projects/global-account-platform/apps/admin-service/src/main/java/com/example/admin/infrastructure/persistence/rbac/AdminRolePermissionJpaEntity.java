package com.example.admin.infrastructure.persistence.rbac;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "admin_role_permissions")
@IdClass(AdminRolePermissionJpaEntity.PK.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminRolePermissionJpaEntity {

    @Id
    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Id
    @Column(name = "permission_key", length = 80, nullable = false)
    private String permissionKey;

    public static class PK implements Serializable {
        private Long roleId;
        private String permissionKey;
        public PK() {}
        public PK(Long roleId, String permissionKey) {
            this.roleId = roleId;
            this.permissionKey = permissionKey;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(roleId, pk.roleId)
                    && Objects.equals(permissionKey, pk.permissionKey);
        }
        @Override public int hashCode() { return Objects.hash(roleId, permissionKey); }
    }
}
