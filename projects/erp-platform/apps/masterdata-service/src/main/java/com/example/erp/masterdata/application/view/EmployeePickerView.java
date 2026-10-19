package com.example.erp.masterdata.application.view;

import com.example.erp.masterdata.domain.employee.Employee;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code GET /employees/picker} element (TASK-ERP-BE-045, masterdata-api.md § GET /employees/picker) — the
 * minimum a console needs to let an approval operator PICK an employee: {@code { id, employeeNumber, name,
 * departmentId, departmentName?, status, accountLinked }}.
 *
 * <p>🔴 Deliberately NOT {@link EmployeeView}: this list opens to {@code ERP_APPROVAL_WRITE} holders who have no
 * masterdata read role, so cost center · job grade · effective period · audit · revision and the IAM
 * {@code accountId} itself are absent by construction — a field added to {@code EmployeeView} later cannot leak
 * here. {@code accountLinked} only says whether a link exists.
 *
 * <p>{@code departmentName} = the department's name effective today (UTC); ABSENT ({@code NON_NULL}) when it does
 * not resolve.
 */
public record EmployeePickerView(String id, String employeeNumber, String name, String departmentId,
                                 @JsonInclude(JsonInclude.Include.NON_NULL) String departmentName,
                                 String status, boolean accountLinked) {

    public static EmployeePickerView from(Employee e, String departmentName) {
        return new EmployeePickerView(e.getId(), e.getEmployeeNumber(), e.getName(), e.getDepartmentId(),
                departmentName, e.getStatus().name(), e.isLinked());
    }
}
