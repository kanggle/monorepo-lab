package com.example.erp.masterdata.application.view;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.domain.authorization.ListDataScope;

/**
 * {@code GET /employees/picker} use-case result (TASK-ERP-BE-045): the page plus which scope produced it —
 * {@code meta.scope} on the wire ({@code TENANT} | {@code SUBTREE}), so a consumer can tell «not in this list»
 * from «does not exist».
 */
public record EmployeePickerResult(PageResult<EmployeePickerView> page, ListDataScope.Kind scope) {
}
