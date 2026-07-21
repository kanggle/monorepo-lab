package com.example.erp.masterdata.integration;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.RetireDepartmentCommand;
import com.example.erp.masterdata.application.view.DepartmentView;
import com.example.erp.masterdata.domain.common.PageResult;
import com.example.erp.masterdata.domain.department.repository.DepartmentListFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-ERP-BE-033 persistence IT — proves the filtered list query + count query
 * against a real MySQL (Testcontainers; H2 forbidden). Rows are isolated from
 * other suites by a per-test unique {@code parentId} so the count assertions are
 * deterministic despite the shared {@code erp} tenant / shared DB.
 *
 * <p>Authoritative on CI-Linux; SKIPPED locally when Docker is unavailable
 * ({@link com.example.testsupport.integration.DockerAvailableCondition}).
 */
class MasterdataListFilterIntegrationTest extends AbstractMasterdataIntegrationTest {

    private static final ActorContext OPERATOR = new ActorContext("op-1", TENANT_ERP,
            Set.of("erp.write", "erp.read"), Set.of("*"));

    @Autowired
    MasterdataApplicationService service;

    @Test
    @DisplayName("AC-2/AC-4: totalElements is the TRUE cross-page count; AC-1: active/parentId/asOf filter")
    void filteredListPaginatesAndCountsTrueTotal() {
        // Isolation anchor: a unique parent; all 25 children hang off it so the
        // parentId filter scopes counts to exactly this test's rows.
        DepartmentView parent = service.createDepartment(new CreateDepartmentCommand(
                OPERATOR, "BE033-ROOT", "FilterRoot", null, LocalDate.of(2026, 1, 1)));
        String parentId = parent.id();

        for (int i = 0; i < 25; i++) {
            service.createDepartment(new CreateDepartmentCommand(
                    OPERATOR, "BE033-CHILD-" + i, "Child-" + i, parentId,
                    LocalDate.of(2026, 1, 1)));
        }

        DepartmentListFilter byParent = new DepartmentListFilter(null, null, parentId);

        // Page 0 of 10 → 10 rows on the page, but totalElements reflects all 25.
        PageResult<DepartmentView> page0 = service.listDepartments(OPERATOR, byParent, 0, 10);
        assertThat(page0.content()).hasSize(10);
        assertThat(page0.totalElements()).isEqualTo(25L);

        // Last page → 5 rows, total still 25 (the AC-2 bug would report 5 here).
        PageResult<DepartmentView> page2 = service.listDepartments(OPERATOR, byParent, 2, 10);
        assertThat(page2.content()).hasSize(5);
        assertThat(page2.totalElements()).isEqualTo(25L);

        // active filter — retire one child, then ACTIVE=24 / RETIRED=1.
        service.retireDepartment(new RetireDepartmentCommand(OPERATOR, page0.content().get(0).id(), "test"));
        PageResult<DepartmentView> active = service.listDepartments(OPERATOR,
                new DepartmentListFilter(null, Boolean.TRUE, parentId), 0, 100);
        assertThat(active.totalElements()).isEqualTo(24L);
        assertThat(active.content()).allMatch(d -> "ACTIVE".equals(d.status()));

        PageResult<DepartmentView> retired = service.listDepartments(OPERATOR,
                new DepartmentListFilter(null, Boolean.FALSE, parentId), 0, 100);
        assertThat(retired.totalElements()).isEqualTo(1L);

        // asOf before any effectiveFrom → no revision is effective yet → 0.
        PageResult<DepartmentView> beforeAll = service.listDepartments(OPERATOR,
                new DepartmentListFilter(LocalDate.of(2025, 1, 1), null, parentId), 0, 100);
        assertThat(beforeAll.totalElements()).isEqualTo(0L);

        // asOf within the active window → all 25 children are effective.
        PageResult<DepartmentView> within = service.listDepartments(OPERATOR,
                new DepartmentListFilter(LocalDate.of(2026, 6, 1), null, parentId), 0, 100);
        assertThat(within.totalElements()).isEqualTo(25L);
    }
}
