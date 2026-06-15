package com.example.erp.masterdata.domain.department;

import com.example.erp.masterdata.domain.common.MasterStatus;
import com.example.erp.masterdata.domain.common.MasterStatusMachine;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Department aggregate root (architecture.md § Aggregate lifecycles § Department).
 * Hierarchical — each Department has at most one parent ({@code parentId}
 * nullable for the root). The set of Department rows is a forest — cycle-free
 * at all times (erp E1).
 *
 * <p>JPA annotations are the single allowed domain↔framework exception
 * (architecture.md § Boundary rules); the invariant logic itself is pure.
 * {@code @Version} gives optimistic locking (transactional T7).
 *
 * <p>The cycle guard is enforced at the application layer
 * ({@code Department.moveParent(...)} requires the resolved ancestry of the
 * new parent — the application service walks the chain via the repository and
 * calls {@link #updateParent} only after the walk succeeds).
 */
@Entity
@Table(name = "departments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Department {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "parent_id", length = 36)
    private String parentId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", length = 16, nullable = false)
    private MasterStatus status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static Department create(String id, String tenantId, String code, String name,
                                    String parentId, EffectivePeriod period, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(now, "now");
        Department d = new Department();
        d.id = id;
        d.tenantId = tenantId;
        d.code = code;
        d.name = name;
        d.parentId = parentId;
        d.status = MasterStatus.ACTIVE;
        d.effectiveFrom = period.effectiveFrom();
        d.effectiveTo = period.effectiveTo();
        d.createdAt = now;
        d.updatedAt = now;
        return d;
    }

    public EffectivePeriod period() {
        return new EffectivePeriod(effectiveFrom, effectiveTo);
    }

    public void rename(String newName, Instant now) {
        Objects.requireNonNull(newName, "newName");
        this.name = newName;
        this.updatedAt = now;
    }

    /**
     * Apply a new parent id. The application service has already walked the
     * ancestry of {@code newParentId} via the repository and verified that
     * this department is not on that path (architecture.md § Reference
     * Integrity model). No-op self-set is rejected here (cycle of length 1).
     */
    public void updateParent(String newParentId, Instant now) {
        if (newParentId != null && newParentId.equals(this.id)) {
            throw new com.example.erp.masterdata.domain.error.DomainErrors
                    .MasterdataParentCycleException(
                    "Department " + id + " cannot be its own parent");
        }
        this.parentId = newParentId;
        this.updatedAt = now;
    }

    public void retire(Instant now) {
        MasterStatusMachine.ensureRetireAllowed(this.status, "Department " + id);
        this.status = MasterStatus.RETIRED;
        this.effectiveTo = now.atZone(ZoneOffset.UTC).toLocalDate();
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == MasterStatus.ACTIVE;
    }
}
