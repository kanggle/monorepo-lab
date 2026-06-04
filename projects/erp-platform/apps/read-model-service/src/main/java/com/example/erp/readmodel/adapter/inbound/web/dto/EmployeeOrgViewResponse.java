package com.example.erp.readmodel.adapter.inbound.web.dto;

import com.example.erp.readmodel.domain.orgview.EmployeeOrgView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/**
 * Response shape for the employee org-view (read-model-api.md § Common shapes).
 * {@code department} / {@code costCenter} / {@code jobGrade} are {@code null}
 * when the referenced master is not yet projected (never fabricated — E5; the
 * unresolved names are surfaced in {@code meta.unresolved}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmployeeOrgViewResponse(
        String id,
        String employeeNumber,
        String name,
        String status,
        EffectivePeriod effectivePeriod,
        DepartmentRef department,
        CostCenterRef costCenter,
        JobGradeRef jobGrade
) {

    public record EffectivePeriod(LocalDate effectiveFrom, LocalDate effectiveTo) {
    }

    public record PathNode(String id, String code, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DepartmentRef(String id, String code, String name, List<PathNode> path) {
    }

    public record CostCenterRef(String id, String code, String name) {
    }

    public record JobGradeRef(String id, String code, String name, int displayOrder) {
    }

    public static EmployeeOrgViewResponse from(EmployeeOrgView view) {
        DepartmentRef dept = view.department() == null ? null
                : new DepartmentRef(
                        view.department().id(),
                        view.department().code(),
                        view.department().name(),
                        view.department().path().stream()
                                .map(p -> new PathNode(p.id(), p.code(), p.name()))
                                .toList());
        CostCenterRef cc = view.costCenter() == null ? null
                : new CostCenterRef(view.costCenter().id(), view.costCenter().code(),
                        view.costCenter().name());
        JobGradeRef jg = view.jobGrade() == null ? null
                : new JobGradeRef(view.jobGrade().id(), view.jobGrade().code(),
                        view.jobGrade().name(), view.jobGrade().displayOrder());
        return new EmployeeOrgViewResponse(
                view.id(),
                view.employeeNumber(),
                view.name(),
                view.status() == null ? null : view.status().name(),
                new EffectivePeriod(view.effectiveFrom(), view.effectiveTo()),
                dept, cc, jg);
    }
}
