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
import java.time.Instant;
import java.util.Objects;

/**
 * TASK-BE-631 / ADR-MONO-083 D4 (Flyway V0052) — {@code operator_group_domain_set_grant}: a set granted to a group
 * (the template fanned out to members as {@code GROUP:<id>} rows). Slice A only READS it (a set's
 * {@code groupCount} and the {@code IN_USE} refusal); the group endpoints that write it are slice B.
 */
@Entity
@Table(name = "operator_group_domain_set_grant")
@IdClass(OperatorGroupDomainSetGrantJpaEntity.PK.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorGroupDomainSetGrantJpaEntity {

    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Id
    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public static OperatorGroupDomainSetGrantJpaEntity create(Long groupId, Long setId, Long grantedBy,
                                                              Instant grantedAt) {
        OperatorGroupDomainSetGrantJpaEntity e = new OperatorGroupDomainSetGrantJpaEntity();
        e.groupId = groupId;
        e.setId = setId;
        e.grantedBy = grantedBy;
        e.grantedAt = grantedAt;
        return e;
    }

    public static class PK implements Serializable {
        private Long groupId;
        private Long setId;

        public PK() {
        }

        public PK(Long groupId, Long setId) {
            this.groupId = groupId;
            this.setId = setId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(groupId, pk.groupId) && Objects.equals(setId, pk.setId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, setId);
        }
    }
}
