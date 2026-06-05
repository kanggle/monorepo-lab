package com.example.erp.readmodel.adapter.inbound.web.dto;

import com.example.erp.readmodel.domain.approval.ApprovalFactProjection;
import com.example.erp.readmodel.domain.approval.ApprovalFactView;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Response shape for an approval fact (read-model-api.md § Approval facts). The
 * {@code subject} is the read-time-resolved master ref ({@code DepartmentSubject}
 * for a DEPARTMENT subject, {@code EmployeeSubject} for an EMPLOYEE subject) —
 * {@code null} when the referenced master is not yet projected (never fabricated
 * — E5; surfaced in {@code meta.unresolved}). {@code submittedAt} /
 * {@code finalizedAt} / {@code lastReason} are ABSENT (NON_NULL) when not
 * applicable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApprovalFactResponse(
        String approvalRequestId,
        String status,
        String subjectType,
        String subjectId,
        Object subject,
        String approverId,
        String submitterId,
        Instant submittedAt,
        Instant finalizedAt,
        String lastReason
) {

    public record PathNode(String id, String code, String name) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DepartmentSubject(String id, String code, String name, List<PathNode> path) {
    }

    public record EmployeeSubject(String id, String employeeNumber, String name) {
    }

    public static ApprovalFactResponse from(ApprovalFactView view) {
        ApprovalFactProjection fact = view.fact();
        Object subject = null;
        if (view.departmentSubject() != null) {
            subject = new DepartmentSubject(
                    view.departmentSubject().id(),
                    view.departmentSubject().code(),
                    view.departmentSubject().name(),
                    view.departmentSubject().path().stream()
                            .map(p -> new PathNode(p.id(), p.code(), p.name()))
                            .toList());
        } else if (view.employeeSubject() != null) {
            subject = new EmployeeSubject(
                    view.employeeSubject().id(),
                    view.employeeSubject().employeeNumber(),
                    view.employeeSubject().name());
        }
        return new ApprovalFactResponse(
                fact.approvalRequestId(),
                fact.status() == null ? null : fact.status().name(),
                fact.subjectType() == null ? null : fact.subjectType().name(),
                fact.subjectId(),
                subject,
                fact.approverId(),
                fact.submitterId(),
                fact.submittedAt(),
                fact.finalizedAt(),
                fact.lastReason());
    }
}
