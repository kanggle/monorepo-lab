package com.example.erp.readmodel.domain.approval;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Assembled approval-fact view (a value object, not a table). Joins the
 * {@link ApprovalFactProjection} with its read-time-resolved subject ref. The
 * subject is {@code null} when the referenced master projection
 * ({@code employee_proj}/{@code department_proj}) has not yet been consumed —
 * recorded in {@link #unresolved()}, never fabricated (E5). Pure Java — no
 * framework annotations.
 */
public final class ApprovalFactView {

    /** Resolved department subject (with root→leaf ancestry path). */
    public record DepartmentSubjectRef(String id, String code, String name, List<PathNode> path) {
        public DepartmentSubjectRef {
            path = path == null ? List.of() : List.copyOf(path);
        }
    }

    /** A single ancestry node in the department path (root→leaf). */
    public record PathNode(String id, String code, String name) {
    }

    /** Trimmed employee subject ref (id + employeeNumber + name only — no org-view enrichment). */
    public record EmployeeSubjectRef(String id, String employeeNumber, String name) {
    }

    public static final String REF_SUBJECT = "subject";

    private final ApprovalFactProjection fact;
    private final DepartmentSubjectRef departmentSubject;
    private final EmployeeSubjectRef employeeSubject;
    private final boolean subjectUnresolved;

    private ApprovalFactView(ApprovalFactProjection fact, DepartmentSubjectRef departmentSubject,
                             EmployeeSubjectRef employeeSubject, boolean subjectUnresolved) {
        this.fact = fact;
        this.departmentSubject = departmentSubject;
        this.employeeSubject = employeeSubject;
        this.subjectUnresolved = subjectUnresolved;
    }

    /** A view with a resolved DEPARTMENT subject. */
    public static ApprovalFactView ofDepartment(ApprovalFactProjection fact,
                                                DepartmentSubjectRef subject) {
        return new ApprovalFactView(fact, subject, null, false);
    }

    /** A view with a resolved EMPLOYEE subject. */
    public static ApprovalFactView ofEmployee(ApprovalFactProjection fact,
                                              EmployeeSubjectRef subject) {
        return new ApprovalFactView(fact, null, subject, false);
    }

    /** A view whose subject master is not yet projected → subject null + meta.unresolved. */
    public static ApprovalFactView ofUnresolvedSubject(ApprovalFactProjection fact) {
        return new ApprovalFactView(fact, null, null, true);
    }

    public ApprovalFactProjection fact() { return fact; }
    public DepartmentSubjectRef departmentSubject() { return departmentSubject; }
    public EmployeeSubjectRef employeeSubject() { return employeeSubject; }

    public List<String> unresolved() {
        return subjectUnresolved ? List.of(REF_SUBJECT) : Collections.emptyList();
    }

    public boolean hasUnresolved() {
        return subjectUnresolved;
    }
}
