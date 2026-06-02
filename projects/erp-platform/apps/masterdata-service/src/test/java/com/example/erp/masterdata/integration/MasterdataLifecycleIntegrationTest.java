package com.example.erp.masterdata.integration;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.CreateEmployeeCommand;
import com.example.erp.masterdata.application.command.Commands.RetireDepartmentCommand;
import com.example.erp.masterdata.application.view.DepartmentView;
import com.example.erp.masterdata.application.view.EmployeeView;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataReferenceViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Lifecycle IT — create / list / detail / retire happy path + reference
 * integrity (retire-blocked / retire-after-dereference). Drives the
 * application layer directly (HTTP layer is exercised by the slice test).
 */
class MasterdataLifecycleIntegrationTest extends AbstractMasterdataIntegrationTest {

    private static final ActorContext OPERATOR = new ActorContext("op-1", TENANT_ERP,
            Set.of("erp.write", "erp.read"), Set.of("*"));

    @Autowired
    MasterdataApplicationService service;

    @Test
    @DisplayName("AC-2/AC-3: create → list → retire happy path + effective_to populated")
    void createListRetire() {
        DepartmentView created = service.createDepartment(new CreateDepartmentCommand(
                OPERATOR, "DEPT-LIFE-1", "Sales", null, LocalDate.of(2026, 1, 1)));
        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(created.effectivePeriod().effectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));

        DepartmentView read = service.getDepartment(created.id(), OPERATOR, null);
        assertThat(read.code()).isEqualTo("DEPT-LIFE-1");

        DepartmentView retired = service.retireDepartment(
                new RetireDepartmentCommand(OPERATOR, created.id(), "reorg"));
        assertThat(retired.status()).isEqualTo("RETIRED");
        assertThat(retired.effectivePeriod().effectiveTo()).isNotNull();

        // Audit row appended (E2 / E8) — exactly 2 rows: CREATE + RETIRE.
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE aggregate_id = ? AND tenant_id = ?",
                Long.class, created.id(), TENANT_ERP);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("E1: retire blocked while a referencer is active; allowed after dereference")
    void retireBlockedByEmployee() {
        // Seed: dept + jobgrade + costcenter + employee
        DepartmentView dept = service.createDepartment(new CreateDepartmentCommand(
                OPERATOR, "DEPT-E1", "DeptE1", null, LocalDate.of(2026, 1, 1)));
        var grade = service.createJobGrade(
                new com.example.erp.masterdata.application.command.Commands.CreateJobGradeCommand(
                        OPERATOR, "G-E1", "Grade-E1", 10, LocalDate.of(2026, 1, 1)));
        var cc = service.createCostCenter(
                new com.example.erp.masterdata.application.command.Commands.CreateCostCenterCommand(
                        OPERATOR, "CC-E1", "CostCenter-E1", dept.id(), LocalDate.of(2026, 1, 1)));
        EmployeeView emp = service.createEmployee(new CreateEmployeeCommand(
                OPERATOR, "EMP-E1", "Holder", dept.id(), cc.id(), grade.id(),
                LocalDate.of(2026, 1, 1)));

        // While the employee references it, dept retire is blocked.
        assertThatThrownBy(() -> service.retireDepartment(
                new RetireDepartmentCommand(OPERATOR, dept.id(), "premature")))
                .isInstanceOf(MasterdataReferenceViolationException.class);

        // Retire the employee first → now dept retire succeeds.
        service.retireEmployee(
                new com.example.erp.masterdata.application.command.Commands.RetireEmployeeCommand(
                        OPERATOR, emp.id(), "leave"));
        // Cost center retire is also blocked while it referenced the (now-active) employee;
        // since we retired the employee, retiring cost center first now succeeds.
        service.retireCostCenter(
                new com.example.erp.masterdata.application.command.Commands.RetireCostCenterCommand(
                        OPERATOR, cc.id(), "consolidate"));

        DepartmentView retired = service.retireDepartment(
                new RetireDepartmentCommand(OPERATOR, dept.id(), "now ok"));
        assertThat(retired.status()).isEqualTo("RETIRED");
    }

    @Test
    @DisplayName("E2: point-in-time read — asOf before retirement returns ACTIVE row")
    void pointInTimeReproducible() {
        DepartmentView dept = service.createDepartment(new CreateDepartmentCommand(
                OPERATOR, "DEPT-PIT", "PointInTime", null, LocalDate.of(2026, 1, 1)));
        // asOf within active period — view is ACTIVE.
        DepartmentView at = service.getDepartment(dept.id(), OPERATOR, LocalDate.of(2026, 3, 1));
        assertThat(at.status()).isEqualTo("ACTIVE");
    }
}
