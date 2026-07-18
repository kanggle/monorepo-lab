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
 * TASK-BE-520 / ADR-MONO-046 D1/D5 (Flyway V0043) — JPA entity for
 * {@code operator_group_member}: the group ↔ operator membership edge. Composite PK
 * {@code (groupId, operatorId)}, both BIGINT surrogate FKs ({@code operator_group.id} /
 * {@code admin_operators.id}). NOT read at evaluation time (fan-out; evaluation reads only
 * the flat substrate). Mirrors the {@code @IdClass} composite-PK pattern of
 * {@link AdminOperatorRoleJpaEntity}.
 */
@Entity
@Table(name = "operator_group_member")
@IdClass(OperatorGroupMemberJpaEntity.PK.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorGroupMemberJpaEntity {

    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Id
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "added_by")
    private Long addedBy;

    public static OperatorGroupMemberJpaEntity create(Long groupId, Long operatorId,
                                                      Instant addedAt, Long addedBy) {
        OperatorGroupMemberJpaEntity e = new OperatorGroupMemberJpaEntity();
        e.groupId = groupId;
        e.operatorId = operatorId;
        e.addedAt = addedAt;
        e.addedBy = addedBy;
        return e;
    }

    public static class PK implements Serializable {
        private Long groupId;
        private Long operatorId;
        public PK() {}
        public PK(Long groupId, Long operatorId) {
            this.groupId = groupId;
            this.operatorId = operatorId;
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(groupId, pk.groupId)
                    && Objects.equals(operatorId, pk.operatorId);
        }
        @Override public int hashCode() { return Objects.hash(groupId, operatorId); }
    }
}
