package com.example.erp.readmodel.domain.projection;

import com.example.erp.readmodel.domain.common.MasterStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Projection of an {@code employee} master (read-only, E5). Maintained by the
 * {@code erp.masterdata.employee.changed.v1} consumer as a single-row upsert
 * keyed by {@code id} (= aggregateId). Its department / cost-center / job-grade
 * references are resolved against the other projections at READ time (the
 * org-view assembly); an unconsumed reference resolves to {@code null}, never a
 * fabricated value. Pure Java — no framework annotations.
 */
public class EmployeeProjection {

    private final String id;
    private String employeeNumber;
    private String name;
    private String departmentId;
    private String costCenterId;
    private String jobGradeId;
    private MasterStatus status;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Instant lastEventAt;
    private String lastEventId;

    public EmployeeProjection(String id, String employeeNumber, String name, String departmentId,
                              String costCenterId, String jobGradeId, MasterStatus status,
                              LocalDate effectiveFrom, LocalDate effectiveTo,
                              Instant lastEventAt, String lastEventId) {
        this.id = Objects.requireNonNull(id, "id");
        this.employeeNumber = employeeNumber;
        this.name = name;
        this.departmentId = departmentId;
        this.costCenterId = costCenterId;
        this.jobGradeId = jobGradeId;
        this.status = status == null ? MasterStatus.ACTIVE : status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    public static EmployeeProjection of(String id, String employeeNumber, String name,
                                        String departmentId, String costCenterId, String jobGradeId,
                                        MasterStatus status, LocalDate effectiveFrom,
                                        LocalDate effectiveTo, Instant lastEventAt,
                                        String lastEventId) {
        return new EmployeeProjection(id, employeeNumber, name, departmentId, costCenterId,
                jobGradeId, status, effectiveFrom, effectiveTo, lastEventAt, lastEventId);
    }

    public void applyUpsert(String employeeNumber, String name, String departmentId,
                            String costCenterId, String jobGradeId, MasterStatus status,
                            LocalDate effectiveFrom, LocalDate effectiveTo,
                            Instant lastEventAt, String lastEventId) {
        this.employeeNumber = employeeNumber;
        this.name = name;
        this.departmentId = departmentId;
        this.costCenterId = costCenterId;
        this.jobGradeId = jobGradeId;
        this.status = status == null ? MasterStatus.ACTIVE : status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    public void applyRetire(LocalDate effectiveTo, Instant lastEventAt, String lastEventId) {
        this.status = MasterStatus.RETIRED;
        if (effectiveTo != null) {
            this.effectiveTo = effectiveTo;
        }
        this.lastEventAt = lastEventAt;
        this.lastEventId = lastEventId;
    }

    public String id() { return id; }
    public String employeeNumber() { return employeeNumber; }
    public String name() { return name; }
    public String departmentId() { return departmentId; }
    public String costCenterId() { return costCenterId; }
    public String jobGradeId() { return jobGradeId; }
    public MasterStatus status() { return status; }
    public LocalDate effectiveFrom() { return effectiveFrom; }
    public LocalDate effectiveTo() { return effectiveTo; }
    public Instant lastEventAt() { return lastEventAt; }
    public String lastEventId() { return lastEventId; }
}
