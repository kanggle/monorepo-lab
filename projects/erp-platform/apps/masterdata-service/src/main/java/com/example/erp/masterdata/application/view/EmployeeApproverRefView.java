package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.employee.Employee;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code GET /employees/{id}/approver-ref} — the minimum an approval line needs to resolve an
 * approver: {@code { id, status, accountId? }}. No name or other PII (masterdata-api.md
 * § Employee ↔ IAM account link). {@code accountId} ABSENT when not linked.
 */
public record EmployeeApproverRefView(String id, String status,
                                      @JsonInclude(JsonInclude.Include.NON_NULL) String accountId) {

    public static EmployeeApproverRefView from(Employee e) {
        return new EmployeeApproverRefView(e.getId(), e.getStatus().name(), e.getAccountId());
    }
}
