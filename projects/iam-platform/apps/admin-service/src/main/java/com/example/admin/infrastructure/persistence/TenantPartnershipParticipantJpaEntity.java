package com.example.admin.infrastructure.persistence;

import com.example.admin.domain.rbac.ScopeSet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * TASK-BE-477 / ADR-MONO-045 D4 — JPA entity for {@code tenant_partnership_participant}:
 * a partner-owned operator bound to an ACTIVE partnership. Composite PK
 * {@code (partnership_id, operator_id)} — both BIGINT surrogate FKs
 * ({@code tenant_partnership.id} / {@code admin_operators.id}) with
 * {@code ON DELETE CASCADE} — mirroring {@code OperatorTenantAssignmentJpaEntity}.
 *
 * <p>{@code participant_scope} is a NULLABLE MySQL {@code JSON} column ({@code null} ⟺
 * the whole {@code delegated_scope}, net-zero narrowing).
 */
@Entity
@Table(name = "tenant_partnership_participant")
@IdClass(TenantPartnershipParticipantJpaEntity.PK.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantPartnershipParticipantJpaEntity {

    @Id
    @Column(name = "partnership_id", nullable = false)
    private Long partnershipId;

    @Id
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "participant_scope")
    private PartnershipScopeJson participantScope;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by")
    private Long assignedBy;

    /** Factory. {@code participantScope} may be {@code null} (⟺ whole delegated_scope). */
    public static TenantPartnershipParticipantJpaEntity create(Long partnershipId, Long operatorId,
                                                               ScopeSet participantScope,
                                                               Long assignedBy, Instant assignedAt) {
        TenantPartnershipParticipantJpaEntity e = new TenantPartnershipParticipantJpaEntity();
        e.partnershipId = partnershipId;
        e.operatorId = operatorId;
        e.participantScope = PartnershipScopeJson.from(participantScope);
        e.assignedBy = assignedBy;
        e.assignedAt = assignedAt;
        return e;
    }

    /** Domain view of the participant scope ({@code null} ⟺ whole delegated_scope). */
    public ScopeSet participantScopeSet() {
        return PartnershipScopeJson.toScopeSet(participantScope);
    }

    public static class PK implements Serializable {
        private Long partnershipId;
        private Long operatorId;

        public PK() {
        }

        public PK(Long partnershipId, Long operatorId) {
            this.partnershipId = partnershipId;
            this.operatorId = operatorId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PK pk)) {
                return false;
            }
            return Objects.equals(partnershipId, pk.partnershipId)
                    && Objects.equals(operatorId, pk.operatorId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(partnershipId, operatorId);
        }
    }
}
