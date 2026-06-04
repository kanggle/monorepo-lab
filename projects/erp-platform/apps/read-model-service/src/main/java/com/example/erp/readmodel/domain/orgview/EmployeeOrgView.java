package com.example.erp.readmodel.domain.orgview;

import com.example.erp.readmodel.domain.common.MasterStatus;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Assembled employee org-view (a value object, not a table). Joins the employee
 * projection with its resolved department-hierarchy path, cost center, and job
 * grade at read time. Any reference whose master projection has not yet been
 * consumed is left {@code null} and its name recorded in {@link #unresolved()}
 * — the field is NEVER fabricated (E5; {@code READ_MODEL_SOURCE_UNAVAILABLE}
 * semantics). Pure Java — no framework annotations.
 */
public final class EmployeeOrgView {

    /** Resolved department with root→leaf ancestry path. */
    public record DepartmentRef(String id, String code, String name, List<PathNode> path) {
        public DepartmentRef {
            path = path == null ? List.of() : List.copyOf(path);
        }
    }

    /** A single ancestry node in the department path (root→leaf). */
    public record PathNode(String id, String code, String name) {
    }

    public record CostCenterRef(String id, String code, String name) {
    }

    public record JobGradeRef(String id, String code, String name, int displayOrder) {
    }

    /** Reference kinds that may be unresolved (matches meta.unresolved values). */
    public static final String REF_DEPARTMENT = "department";
    public static final String REF_COST_CENTER = "costCenter";
    public static final String REF_JOB_GRADE = "jobGrade";

    private final String id;
    private final String employeeNumber;
    private final String name;
    private final MasterStatus status;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final DepartmentRef department;
    private final CostCenterRef costCenter;
    private final JobGradeRef jobGrade;
    private final List<String> unresolved;

    private EmployeeOrgView(String id, String employeeNumber, String name, MasterStatus status,
                            LocalDate effectiveFrom, LocalDate effectiveTo,
                            DepartmentRef department, CostCenterRef costCenter,
                            JobGradeRef jobGrade, List<String> unresolved) {
        this.id = Objects.requireNonNull(id, "id");
        this.employeeNumber = employeeNumber;
        this.name = name;
        this.status = status;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.department = department;
        this.costCenter = costCenter;
        this.jobGrade = jobGrade;
        this.unresolved = List.copyOf(unresolved);
    }

    /**
     * Builder that records each unresolved reference in declaration order
     * (department, costCenter, jobGrade) so {@code meta.unresolved} is stable.
     */
    public static final class Builder {
        private final String id;
        private final String employeeNumber;
        private final String name;
        private final MasterStatus status;
        private final LocalDate effectiveFrom;
        private final LocalDate effectiveTo;
        private DepartmentRef department;
        private CostCenterRef costCenter;
        private JobGradeRef jobGrade;
        private final List<String> unresolved = new ArrayList<>();

        public Builder(String id, String employeeNumber, String name, MasterStatus status,
                       LocalDate effectiveFrom, LocalDate effectiveTo) {
            this.id = id;
            this.employeeNumber = employeeNumber;
            this.name = name;
            this.status = status;
            this.effectiveFrom = effectiveFrom;
            this.effectiveTo = effectiveTo;
        }

        /**
         * Records a reference: when {@code ref} is non-null it is set; when the
         * employee declares the reference id but the master is not yet projected
         * (caller passes a null ref with {@code referenced=true}), the reference
         * name is added to the unresolved list. A reference the employee does not
         * declare at all (id null) is simply absent (not "unresolved").
         */
        public Builder department(DepartmentRef ref, boolean referencedButMissing) {
            this.department = ref;
            if (ref == null && referencedButMissing) {
                unresolved.add(REF_DEPARTMENT);
            }
            return this;
        }

        public Builder costCenter(CostCenterRef ref, boolean referencedButMissing) {
            this.costCenter = ref;
            if (ref == null && referencedButMissing) {
                unresolved.add(REF_COST_CENTER);
            }
            return this;
        }

        public Builder jobGrade(JobGradeRef ref, boolean referencedButMissing) {
            this.jobGrade = ref;
            if (ref == null && referencedButMissing) {
                unresolved.add(REF_JOB_GRADE);
            }
            return this;
        }

        public EmployeeOrgView build() {
            return new EmployeeOrgView(id, employeeNumber, name, status, effectiveFrom,
                    effectiveTo, department, costCenter, jobGrade, unresolved);
        }
    }

    public String id() { return id; }
    public String employeeNumber() { return employeeNumber; }
    public String name() { return name; }
    public MasterStatus status() { return status; }
    public LocalDate effectiveFrom() { return effectiveFrom; }
    public LocalDate effectiveTo() { return effectiveTo; }
    public DepartmentRef department() { return department; }
    public CostCenterRef costCenter() { return costCenter; }
    public JobGradeRef jobGrade() { return jobGrade; }

    /** Reference names that could not be resolved (empty when all resolved). */
    public List<String> unresolved() {
        return Collections.unmodifiableList(unresolved);
    }

    public boolean hasUnresolved() {
        return !unresolved.isEmpty();
    }
}
