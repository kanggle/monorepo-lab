package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.employee.link.EmployeeAccountLinkProposal;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * {@code EmployeeAccountLinkProposal} wire shape (masterdata-api.md § Employee ↔ IAM account
 * link). Optional fields are ABSENT when null: {@code reason}, the decision triple
 * ({@code decidedBy}/{@code decidedAt}/{@code decisionReason} — only once decided), and the
 * display pair {@code employeeName}/{@code employeeNumber} (only on {@code GET
 * /account-link-proposals/mine}, where the account owner has no other way to see whom the
 * proposal is about).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmployeeAccountLinkProposalView(String id, String employeeId, String accountId,
                                              String status, String proposedBy, Instant proposedAt,
                                              String reason, String decidedBy, Instant decidedAt,
                                              String decisionReason,
                                              String employeeName, String employeeNumber) {

    public static EmployeeAccountLinkProposalView from(EmployeeAccountLinkProposal p) {
        return new EmployeeAccountLinkProposalView(p.getId(), p.getEmployeeId(), p.getAccountId(),
                p.getStatus().name(), p.getProposedBy(), p.getProposedAt(), p.getReason(),
                p.getDecidedBy(), p.getDecidedAt(), p.getDecisionReason(), null, null);
    }

    /** {@code /mine} element — carries the employee's display name + number. */
    public static EmployeeAccountLinkProposalView withEmployee(EmployeeAccountLinkProposal p, Employee e) {
        EmployeeAccountLinkProposalView v = from(p);
        return new EmployeeAccountLinkProposalView(v.id(), v.employeeId(), v.accountId(), v.status(),
                v.proposedBy(), v.proposedAt(), v.reason(), v.decidedBy(), v.decidedAt(),
                v.decisionReason(), e == null ? null : e.getName(),
                e == null ? null : e.getEmployeeNumber());
    }
}
