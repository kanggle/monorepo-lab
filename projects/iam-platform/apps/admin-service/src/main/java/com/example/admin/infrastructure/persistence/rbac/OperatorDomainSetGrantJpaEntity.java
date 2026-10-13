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
 * TASK-BE-631 / ADR-MONO-083 D4 (Flyway V0052) — {@code operator_domain_set_grant}: operator × tenant × set ×
 * SOURCE. The source ({@code DIRECT} · {@code MIGRATION} · {@code GROUP:<id>}) is part of the primary key, so the same
 * set from several sources is several rows and each revoke removes its own source's rows only.
 */
@Entity
@Table(name = "operator_domain_set_grant")
@IdClass(OperatorDomainSetGrantJpaEntity.PK.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperatorDomainSetGrantJpaEntity {

    @Id
    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Id
    @Column(name = "tenant_id", length = 32, nullable = false)
    private String tenantId;

    @Id
    @Column(name = "set_id", nullable = false)
    private Long setId;

    @Id
    @Column(name = "source_key", length = 48, nullable = false)
    private String sourceKey;

    @Column(name = "group_origin")
    private Long groupOrigin;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public static OperatorDomainSetGrantJpaEntity create(Long operatorId, String tenantId, Long setId,
                                                         String sourceKey, Long groupOrigin, Long grantedBy,
                                                         Instant grantedAt) {
        OperatorDomainSetGrantJpaEntity e = new OperatorDomainSetGrantJpaEntity();
        e.operatorId = operatorId;
        e.tenantId = tenantId;
        e.setId = setId;
        e.sourceKey = sourceKey;
        e.groupOrigin = groupOrigin;
        e.grantedBy = grantedBy;
        e.grantedAt = grantedAt;
        return e;
    }

    public static class PK implements Serializable {
        private Long operatorId;
        private String tenantId;
        private Long setId;
        private String sourceKey;

        public PK() {
        }

        public PK(Long operatorId, String tenantId, Long setId, String sourceKey) {
            this.operatorId = operatorId;
            this.tenantId = tenantId;
            this.setId = setId;
            this.sourceKey = sourceKey;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PK pk)) return false;
            return Objects.equals(operatorId, pk.operatorId) && Objects.equals(tenantId, pk.tenantId)
                    && Objects.equals(setId, pk.setId) && Objects.equals(sourceKey, pk.sourceKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(operatorId, tenantId, setId, sourceKey);
        }
    }
}
