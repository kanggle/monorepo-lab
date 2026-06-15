package com.example.erp.masterdata.domain.employee;

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
 * Employee aggregate root — organization attributes only (architecture.md §
 * Aggregate lifecycles § Employee). Each effective revision references one
 * Department + CostCenter + JobGrade. HR depth (payroll/attendance/evaluation)
 * is out of scope per PROJECT.md.
 */
@Entity
@Table(name = "employees")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "employee_number", length = 64, nullable = false)
    private String employeeNumber;

    @Column(name = "name", length = 256, nullable = false)
    private String name;

    @Column(name = "department_id", length = 36, nullable = false)
    private String departmentId;

    @Column(name = "cost_center_id", length = 36, nullable = false)
    private String costCenterId;

    @Column(name = "job_grade_id", length = 36, nullable = false)
    private String jobGradeId;

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

    public static Employee create(String id, String tenantId, String employeeNumber,
                                  String name, String departmentId, String costCenterId,
                                  String jobGradeId, EffectivePeriod period, Instant now) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(employeeNumber, "employeeNumber");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(departmentId, "departmentId");
        Objects.requireNonNull(costCenterId, "costCenterId");
        Objects.requireNonNull(jobGradeId, "jobGradeId");
        Objects.requireNonNull(period, "period");
        Employee e = new Employee();
        e.id = id;
        e.tenantId = tenantId;
        e.employeeNumber = employeeNumber;
        e.name = name;
        e.departmentId = departmentId;
        e.costCenterId = costCenterId;
        e.jobGradeId = jobGradeId;
        e.status = MasterStatus.ACTIVE;
        e.effectiveFrom = period.effectiveFrom();
        e.effectiveTo = period.effectiveTo();
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public EffectivePeriod period() {
        return new EffectivePeriod(effectiveFrom, effectiveTo);
    }

    public void updateAttributes(String newName, String newDepartmentId,
                                 String newCostCenterId, String newJobGradeId, Instant now) {
        if (newName != null) this.name = newName;
        if (newDepartmentId != null) this.departmentId = newDepartmentId;
        if (newCostCenterId != null) this.costCenterId = newCostCenterId;
        if (newJobGradeId != null) this.jobGradeId = newJobGradeId;
        this.updatedAt = now;
    }

    public void retire(Instant now) {
        MasterStatusMachine.ensureRetireAllowed(this.status, "Employee " + id);
        this.status = MasterStatus.RETIRED;
        this.effectiveTo = now.atZone(ZoneOffset.UTC).toLocalDate();
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == MasterStatus.ACTIVE;
    }
}
