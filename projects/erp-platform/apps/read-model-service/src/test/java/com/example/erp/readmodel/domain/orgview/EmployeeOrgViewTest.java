package com.example.erp.readmodel.domain.orgview;

import com.example.erp.readmodel.domain.common.MasterStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EmployeeOrgView} assembly: resolved / unresolved
 * reference cases + the department path order. Unresolved references must be
 * recorded in {@code meta.unresolved} and the field left null (never fabricated,
 * E5).
 */
class EmployeeOrgViewTest {

    @Test
    void allReferencesResolvedYieldsNoUnresolved() {
        EmployeeOrgView view = new EmployeeOrgView.Builder(
                "emp-1", "E-1001", "홍길동", MasterStatus.ACTIVE,
                LocalDate.parse("2026-01-01"), null)
                .department(new EmployeeOrgView.DepartmentRef("dept-1", "SALES", "영업본부",
                        List.of(new EmployeeOrgView.PathNode("hq", "HQ", "본사"),
                                new EmployeeOrgView.PathNode("dept-1", "SALES", "영업본부"))), false)
                .costCenter(new EmployeeOrgView.CostCenterRef("cc-1", "CC-100", "영업원가센터"), false)
                .jobGrade(new EmployeeOrgView.JobGradeRef("jg-1", "G3", "사원", 30), false)
                .build();

        assertThat(view.hasUnresolved()).isFalse();
        assertThat(view.unresolved()).isEmpty();
        assertThat(view.department().path()).extracting(EmployeeOrgView.PathNode::code)
                .containsExactly("HQ", "SALES"); // root -> leaf
    }

    @Test
    void missingDepartmentRecordsUnresolvedAndLeavesNull() {
        EmployeeOrgView view = new EmployeeOrgView.Builder(
                "emp-1", "E-1001", "홍길동", MasterStatus.ACTIVE, null, null)
                .department(null, true)
                .costCenter(new EmployeeOrgView.CostCenterRef("cc-1", "CC-100", "cc"), false)
                .jobGrade(new EmployeeOrgView.JobGradeRef("jg-1", "G3", "사원", 30), false)
                .build();

        assertThat(view.department()).isNull();
        assertThat(view.unresolved()).containsExactly(EmployeeOrgView.REF_DEPARTMENT);
    }

    @Test
    void multipleUnresolvedRecordedInDeclarationOrder() {
        EmployeeOrgView view = new EmployeeOrgView.Builder(
                "emp-1", "E-1001", "홍길동", MasterStatus.ACTIVE, null, null)
                .department(null, true)
                .costCenter(null, true)
                .jobGrade(null, true)
                .build();

        assertThat(view.unresolved()).containsExactly(
                EmployeeOrgView.REF_DEPARTMENT,
                EmployeeOrgView.REF_COST_CENTER,
                EmployeeOrgView.REF_JOB_GRADE);
    }

    @Test
    void referenceNotDeclaredIsNotUnresolved() {
        // The employee declares no cost-center id at all (id null, not missing-master).
        EmployeeOrgView view = new EmployeeOrgView.Builder(
                "emp-1", "E-1001", "홍길동", MasterStatus.ACTIVE, null, null)
                .department(new EmployeeOrgView.DepartmentRef("dept-1", "S", "영업", List.of()), false)
                .costCenter(null, false)
                .jobGrade(null, false)
                .build();

        assertThat(view.costCenter()).isNull();
        assertThat(view.unresolved()).isEmpty();
    }
}
