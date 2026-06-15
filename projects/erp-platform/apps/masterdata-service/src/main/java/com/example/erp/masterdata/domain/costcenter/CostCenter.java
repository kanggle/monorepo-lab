package com.example.erp.masterdata.domain.costcenter;

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

@Entity
@Table(name = "cost_centers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CostCenter {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "code", length = 64, nullable = false)
    private String code;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "department_id", length = 36, nullable = false)
    private String departmentId;

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

    public static CostCenter create(String id, String tenantId, String code, String name,
                                    String departmentId, EffectivePeriod period, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(departmentId, "departmentId");
        Objects.requireNonNull(period, "period");
        CostCenter c = new CostCenter();
        c.id = id;
        c.tenantId = tenantId;
        c.code = code;
        c.name = name;
        c.departmentId = departmentId;
        c.status = MasterStatus.ACTIVE;
        c.effectiveFrom = period.effectiveFrom();
        c.effectiveTo = period.effectiveTo();
        c.createdAt = now;
        c.updatedAt = now;
        return c;
    }

    public EffectivePeriod period() {
        return new EffectivePeriod(effectiveFrom, effectiveTo);
    }

    public void updateAttributes(String newName, String newDepartmentId, Instant now) {
        if (newName != null) this.name = newName;
        if (newDepartmentId != null) this.departmentId = newDepartmentId;
        this.updatedAt = now;
    }

    public void retire(Instant now) {
        MasterStatusMachine.ensureRetireAllowed(this.status, "CostCenter " + id);
        this.status = MasterStatus.RETIRED;
        this.effectiveTo = now.atZone(ZoneOffset.UTC).toLocalDate();
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == MasterStatus.ACTIVE;
    }
}
