package com.example.erp.readmodel.domain.projection;

import com.example.erp.readmodel.domain.common.MasterStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the four projection aggregates' upsert + retire-mark behaviour
 * (architecture.md § Testing Strategy — domain). RETIRED is a logical status
 * (row retained), never a delete (E2).
 */
class ProjectionUpsertRetireTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void departmentUpsertReplacesBusinessFieldsAndParentForParentMoved() {
        DepartmentProjection dept = DepartmentProjection.of(
                "dept-1", "SALES", "영업", "root", MasterStatus.ACTIVE,
                LocalDate.parse("2026-01-01"), null, T0, "evt-1");

        dept.applyUpsert("SALES", "영업본부", "hq", MasterStatus.ACTIVE,
                LocalDate.parse("2026-01-01"), null, T1, "evt-2");

        assertThat(dept.id()).isEqualTo("dept-1");
        assertThat(dept.name()).isEqualTo("영업본부");
        assertThat(dept.parentId()).isEqualTo("hq"); // PARENT_MOVED reflected
        assertThat(dept.lastEventId()).isEqualTo("evt-2");
        assertThat(dept.status()).isEqualTo(MasterStatus.ACTIVE);
    }

    @Test
    void departmentRetireMarksStatusAndRetainsRow() {
        DepartmentProjection dept = DepartmentProjection.of(
                "dept-1", "SALES", "영업", "root", MasterStatus.ACTIVE,
                LocalDate.parse("2026-01-01"), null, T0, "evt-1");

        dept.applyRetire(LocalDate.parse("2026-03-01"), T1, "evt-retire");

        assertThat(dept.status()).isEqualTo(MasterStatus.RETIRED);
        assertThat(dept.effectiveTo()).isEqualTo(LocalDate.parse("2026-03-01"));
        assertThat(dept.code()).isEqualTo("SALES"); // retained, not deleted
        assertThat(dept.lastEventId()).isEqualTo("evt-retire");
    }

    @Test
    void employeeUpsertAndRetire() {
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", "dept-1", "cc-1", "jg-1",
                MasterStatus.ACTIVE, LocalDate.parse("2026-01-01"), null, T0, "evt-1");

        emp.applyUpsert("E-1001", "홍길동", "dept-2", "cc-2", "jg-2",
                MasterStatus.ACTIVE, LocalDate.parse("2026-01-01"), null, T1, "evt-2");
        assertThat(emp.departmentId()).isEqualTo("dept-2");
        assertThat(emp.costCenterId()).isEqualTo("cc-2");
        assertThat(emp.jobGradeId()).isEqualTo("jg-2");

        emp.applyRetire(LocalDate.parse("2026-04-01"), T1, "evt-r");
        assertThat(emp.status()).isEqualTo(MasterStatus.RETIRED);
        assertThat(emp.effectiveTo()).isEqualTo(LocalDate.parse("2026-04-01"));
        assertThat(emp.employeeNumber()).isEqualTo("E-1001");
    }

    @Test
    void jobGradeUpsertAndRetire() {
        JobGradeProjection jg = JobGradeProjection.of(
                "jg-1", "G3", "사원", 30, MasterStatus.ACTIVE, null, null, T0, "evt-1");
        jg.applyUpsert("G3", "선임", 25, MasterStatus.ACTIVE, null, null, T1, "evt-2");
        assertThat(jg.name()).isEqualTo("선임");
        assertThat(jg.displayOrder()).isEqualTo(25);

        jg.applyRetire(LocalDate.parse("2026-05-01"), T1, "evt-r");
        assertThat(jg.status()).isEqualTo(MasterStatus.RETIRED);
    }

    @Test
    void costCenterUpsertAndRetire() {
        CostCenterProjection cc = CostCenterProjection.of(
                "cc-1", "CC-100", "영업원가센터", "dept-1", MasterStatus.ACTIVE,
                null, null, T0, "evt-1");
        cc.applyUpsert("CC-100", "영업원가센터(개정)", "dept-2", MasterStatus.ACTIVE,
                null, null, T1, "evt-2");
        assertThat(cc.name()).isEqualTo("영업원가센터(개정)");
        assertThat(cc.departmentId()).isEqualTo("dept-2");

        cc.applyRetire(null, T1, "evt-r");
        assertThat(cc.status()).isEqualTo(MasterStatus.RETIRED);
        assertThat(cc.code()).isEqualTo("CC-100");
    }
}
