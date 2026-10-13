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

/**
 * TASK-BE-631 / ADR-MONO-083 (Flyway V0052) — {@code domain_permission_set_role}: set → catalog role. Composite PK
 * {@code (setId, domainKey, roleName)}; {@code set_id} is the set's BIGINT surrogate.
 */
@Entity
@Table(name = "domain_permission_set_role")
@IdClass(DomainPermissionSetRoleJpaEntity.PK.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DomainPermissionSetRoleJpaEntity {

    @Id
    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Id
    @Column(name = "domain_key", length = 32, nullable = false)
    private String domainKey;

    @Id
    @Column(name = "role_name", length = 40, nullable = false)
    private String roleName;

    public static DomainPermissionSetRoleJpaEntity create(Long setId, String domainKey, String roleName) {
        DomainPermissionSetRoleJpaEntity e = new DomainPermissionSetRoleJpaEntity();
        e.setId = setId;
        e.domainKey = domainKey;
        e.roleName = roleName;
        return e;
    }

    public static class PK implements Serializable {
        private Long setId;
        private String domainKey;
        private String roleName;

        public PK() {
        }

        public PK(Long setId, String domainKey, String roleName) {
            this.setId = setId;
            this.domainKey = domainKey;
            this.roleName = roleName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(setId, pk.setId) && Objects.equals(domainKey, pk.domainKey)
                    && Objects.equals(roleName, pk.roleName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(setId, domainKey, roleName);
        }
    }
}
