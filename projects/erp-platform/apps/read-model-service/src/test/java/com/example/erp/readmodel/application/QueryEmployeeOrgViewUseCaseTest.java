package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.port.outbound.OrgViewMetricsPort;
import com.example.erp.readmodel.application.query.EmployeeOrgViewPage;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.error.ReadModelNotFoundException;
import com.example.erp.readmodel.domain.orgview.EmployeeOrgView;
import com.example.erp.readmodel.domain.projection.CostCenterProjection;
import com.example.erp.readmodel.domain.projection.DepartmentProjection;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;
import com.example.erp.readmodel.domain.projection.JobGradeProjection;
import com.example.erp.readmodel.domain.projection.repository.CostCenterProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.EmployeeProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.JobGradeProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class QueryEmployeeOrgViewUseCaseTest {

    @Mock EmployeeProjectionRepository employeeRepository;
    @Mock DepartmentProjectionRepository departmentRepository;
    @Mock CostCenterProjectionRepository costCenterRepository;
    @Mock JobGradeProjectionRepository jobGradeRepository;
    @Mock OrgViewMetricsPort metrics;

    @InjectMocks QueryEmployeeOrgViewUseCase useCase;

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setDepth() {
        ReflectionTestUtils.setField(useCase, "departmentPathMaxDepth", 32);
    }

    @Test
    void getOneResolvesAllReferencesWithDepartmentPath() {
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", "dept-1", "cc-1", "jg-1",
                MasterStatus.ACTIVE, null, null, T0, "e");
        DepartmentProjection leaf = DepartmentProjection.of(
                "dept-1", "SALES", "영업본부", "hq", MasterStatus.ACTIVE, null, null, T0, "e");
        DepartmentProjection root = DepartmentProjection.of(
                "hq", "HQ", "본사", null, MasterStatus.ACTIVE, null, null, T0, "e");

        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(emp));
        when(departmentRepository.findAllByIds(List.of("dept-1")))
                .thenReturn(Map.of("dept-1", leaf));
        when(departmentRepository.findById("hq")).thenReturn(Optional.of(root));
        when(costCenterRepository.findAllByIds(List.of("cc-1")))
                .thenReturn(Map.of("cc-1", CostCenterProjection.of(
                        "cc-1", "CC-100", "영업원가센터", "dept-1", MasterStatus.ACTIVE, null, null, T0, "e")));
        when(jobGradeRepository.findAllByIds(List.of("jg-1")))
                .thenReturn(Map.of("jg-1", JobGradeProjection.of(
                        "jg-1", "G3", "사원", 30, MasterStatus.ACTIVE, null, null, T0, "e")));

        EmployeeOrgView view = useCase.getOne("emp-1");

        assertThat(view.hasUnresolved()).isFalse();
        assertThat(view.department().path()).extracting(EmployeeOrgView.PathNode::code)
                .containsExactly("HQ", "SALES");
        assertThat(view.costCenter().code()).isEqualTo("CC-100");
        assertThat(view.jobGrade().displayOrder()).isEqualTo(30);
    }

    @Test
    void getOneWithMissingDepartmentYieldsNullPlusUnresolvedAndMetric() {
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", "dept-missing", "cc-1", "jg-1",
                MasterStatus.ACTIVE, null, null, T0, "e");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(emp));
        when(departmentRepository.findAllByIds(List.of("dept-missing"))).thenReturn(Map.of());
        when(costCenterRepository.findAllByIds(List.of("cc-1")))
                .thenReturn(Map.of("cc-1", CostCenterProjection.of(
                        "cc-1", "CC-100", "cc", "dept-1", MasterStatus.ACTIVE, null, null, T0, "e")));
        when(jobGradeRepository.findAllByIds(List.of("jg-1")))
                .thenReturn(Map.of("jg-1", JobGradeProjection.of(
                        "jg-1", "G3", "사원", 30, MasterStatus.ACTIVE, null, null, T0, "e")));

        EmployeeOrgView view = useCase.getOne("emp-1");

        assertThat(view.department()).isNull();
        assertThat(view.unresolved()).containsExactly(EmployeeOrgView.REF_DEPARTMENT);
        verify(metrics).recordUnresolved(EmployeeOrgView.REF_DEPARTMENT);
    }

    @Test
    void getOneUnknownEmployeeThrowsNotFound() {
        when(employeeRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.getOne("ghost"))
                .isInstanceOf(ReadModelNotFoundException.class);
    }

    @Test
    void listAppliesSubtreeFilterAndCountsTotal() {
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", "dept-1", null, null,
                MasterStatus.ACTIVE, null, null, T0, "e");
        when(departmentRepository.findSubtreeIds("dept-root", 32))
                .thenReturn(List.of("dept-root", "dept-1"));
        when(employeeRepository.findPage(MasterStatus.ACTIVE,
                List.of("dept-root", "dept-1"), 0, 20)).thenReturn(List.of(emp));
        when(employeeRepository.count(MasterStatus.ACTIVE, List.of("dept-root", "dept-1")))
                .thenReturn(1L);
        when(departmentRepository.findAllByIds(any())).thenReturn(Map.of("dept-1",
                DepartmentProjection.of("dept-1", "S", "영업", null, MasterStatus.ACTIVE,
                        null, null, T0, "e")));
        lenient().when(costCenterRepository.findAllByIds(any())).thenReturn(Map.of());
        lenient().when(jobGradeRepository.findAllByIds(any())).thenReturn(Map.of());

        EmployeeOrgViewPage result = useCase.list(MasterStatus.ACTIVE, "dept-root", 0, 20);

        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).department().code()).isEqualTo("S");
    }

    // ── org_scope read filter (TASK-ERP-BE-008) ──

    @Test
    void listWithOrgScopeNarrowsToSubtreeUnion() {
        // org_scope=[sales-root] expands to its subtree; the list is filtered to it.
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", "sales-east", null, null,
                MasterStatus.ACTIVE, null, null, T0, "e");
        when(departmentRepository.findSubtreeIds("sales-root", 32))
                .thenReturn(List.of("sales-root", "sales-east"));
        when(employeeRepository.findPage(MasterStatus.ACTIVE,
                List.of("sales-root", "sales-east"), 0, 20)).thenReturn(List.of(emp));
        when(employeeRepository.count(MasterStatus.ACTIVE, List.of("sales-root", "sales-east")))
                .thenReturn(1L);
        when(departmentRepository.findAllByIds(any())).thenReturn(Map.of("sales-east",
                DepartmentProjection.of("sales-east", "SE", "영업동부", "sales-root",
                        MasterStatus.ACTIVE, null, null, T0, "e")));
        lenient().when(departmentRepository.findById("sales-root")).thenReturn(Optional.of(
                DepartmentProjection.of("sales-root", "SR", "영업", null,
                        MasterStatus.ACTIVE, null, null, T0, "e")));
        lenient().when(costCenterRepository.findAllByIds(any())).thenReturn(Map.of());
        lenient().when(jobGradeRepository.findAllByIds(any())).thenReturn(Map.of());

        EmployeeOrgViewPage result = useCase.list(MasterStatus.ACTIVE, null,
                List.of("sales-root"), 0, 20);

        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.content()).hasSize(1);
    }

    @Test
    void listWithExplicitAndOrgScopeFiltersIntersect() {
        // ?departmentId=sales (→ {sales-root,sales-east,sales-west}) ∩ org_scope=[sales-east]
        // (→ {sales-east}) = {sales-east} only.
        when(departmentRepository.findSubtreeIds("sales-root", 32))
                .thenReturn(List.of("sales-root", "sales-east", "sales-west"));
        when(departmentRepository.findSubtreeIds("sales-east", 32))
                .thenReturn(List.of("sales-east"));
        when(employeeRepository.findPage(MasterStatus.ACTIVE, List.of("sales-east"), 0, 20))
                .thenReturn(List.of());
        when(employeeRepository.count(MasterStatus.ACTIVE, List.of("sales-east")))
                .thenReturn(0L);

        EmployeeOrgViewPage result = useCase.list(MasterStatus.ACTIVE, "sales-root",
                List.of("sales-east"), 0, 20);

        assertThat(result.totalElements()).isEqualTo(0L);
    }

    @Test
    void listWithNullOrgScopeIsNetZeroNoNarrowing() {
        // org_scope=null → the department filter is null (no narrowing), exactly
        // the BE-007 behavior.
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", null, null, null,
                MasterStatus.ACTIVE, null, null, T0, "e");
        when(employeeRepository.findPage(MasterStatus.ACTIVE, null, 0, 20))
                .thenReturn(List.of(emp));
        when(employeeRepository.count(MasterStatus.ACTIVE, null)).thenReturn(1L);
        lenient().when(costCenterRepository.findAllByIds(any())).thenReturn(Map.of());
        lenient().when(jobGradeRepository.findAllByIds(any())).thenReturn(Map.of());

        EmployeeOrgViewPage result = useCase.list(MasterStatus.ACTIVE, null, null, 0, 20);

        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    void listWithEmptyOrgScopeZeroScopeYieldsEmptyPage() {
        // explicit org_scope=[] (zero-scope) expands to {} → empty filter → empty page.
        when(employeeRepository.findPage(MasterStatus.ACTIVE, List.of(), 0, 20))
                .thenReturn(List.of());
        when(employeeRepository.count(MasterStatus.ACTIVE, List.of())).thenReturn(0L);

        EmployeeOrgViewPage result = useCase.list(MasterStatus.ACTIVE, null, List.of(), 0, 20);

        assertThat(result.totalElements()).isEqualTo(0L);
        assertThat(result.content()).isEmpty();
    }

    @Test
    void getOneInOrgScopeResolves() {
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", "sales-east", null, null,
                MasterStatus.ACTIVE, null, null, T0, "e");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(emp));
        when(departmentRepository.findSubtreeIds("sales-root", 32))
                .thenReturn(List.of("sales-root", "sales-east"));
        when(departmentRepository.findAllByIds(List.of("sales-east"))).thenReturn(Map.of(
                "sales-east", DepartmentProjection.of("sales-east", "SE", "동부", "sales-root",
                        MasterStatus.ACTIVE, null, null, T0, "e")));
        when(departmentRepository.findById("sales-root")).thenReturn(Optional.of(
                DepartmentProjection.of("sales-root", "SR", "영업", null,
                        MasterStatus.ACTIVE, null, null, T0, "e")));
        lenient().when(costCenterRepository.findAllByIds(any())).thenReturn(Map.of());
        lenient().when(jobGradeRepository.findAllByIds(any())).thenReturn(Map.of());

        EmployeeOrgView view = useCase.getOne("emp-1", List.of("sales-root"));

        assertThat(view.id()).isEqualTo("emp-1");
    }

    @Test
    void getOneOutOfOrgScopeThrowsNotFound() {
        // Employee's department (eng-platform) is outside org_scope=[sales-root] → 404.
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", "eng-platform", null, null,
                MasterStatus.ACTIVE, null, null, T0, "e");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(emp));
        when(departmentRepository.findSubtreeIds("sales-root", 32))
                .thenReturn(List.of("sales-root", "sales-east"));

        assertThatThrownBy(() -> useCase.getOne("emp-1", List.of("sales-root")))
                .isInstanceOf(ReadModelNotFoundException.class);
    }

    @Test
    void getOneUnprojectedDepartmentConservativelyExcludedUnderScope() {
        // Department not (yet) projected → cannot prove in-scope → 404 (E5 conservative).
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", null, null, null,
                MasterStatus.ACTIVE, null, null, T0, "e");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(emp));

        assertThatThrownBy(() -> useCase.getOne("emp-1", List.of("sales-root")))
                .isInstanceOf(ReadModelNotFoundException.class);
    }

    @Test
    void departmentPathWalkTerminatesOnCycleViaVisitedGuard() {
        // Defensive: dept-a -> dept-b -> dept-a (a malformed cycle). The walk
        // must terminate (visited guard) rather than loop forever.
        EmployeeProjection emp = EmployeeProjection.of(
                "emp-1", "E-1001", "홍길동", "dept-a", null, null,
                MasterStatus.ACTIVE, null, null, T0, "e");
        DepartmentProjection a = DepartmentProjection.of(
                "dept-a", "A", "A", "dept-b", MasterStatus.ACTIVE, null, null, T0, "e");
        DepartmentProjection b = DepartmentProjection.of(
                "dept-b", "B", "B", "dept-a", MasterStatus.ACTIVE, null, null, T0, "e");
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(emp));
        when(departmentRepository.findAllByIds(List.of("dept-a")))
                .thenReturn(Map.of("dept-a", a));
        when(departmentRepository.findById("dept-b")).thenReturn(Optional.of(b));
        // dept-b's parent dept-a is already visited → walk stops; never re-queries a.

        EmployeeOrgView view = useCase.getOne("emp-1");

        assertThat(view.department()).isNotNull();
        assertThat(view.department().path()).extracting(EmployeeOrgView.PathNode::code)
                .containsExactly("B", "A"); // root(b) -> leaf(a), cycle broken
    }
}
