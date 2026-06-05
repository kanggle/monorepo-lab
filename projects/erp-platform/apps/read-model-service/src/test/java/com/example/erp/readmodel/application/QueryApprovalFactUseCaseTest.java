package com.example.erp.readmodel.application;

import com.example.erp.readmodel.application.port.outbound.OrgViewMetricsPort;
import com.example.erp.readmodel.application.query.ApprovalFactPage;
import com.example.erp.readmodel.domain.approval.ApprovalFactProjection;
import com.example.erp.readmodel.domain.approval.ApprovalFactView;
import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;
import com.example.erp.readmodel.domain.approval.repository.ApprovalFactFilter;
import com.example.erp.readmodel.domain.approval.repository.ApprovalFactProjectionRepository;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.error.ReadModelNotFoundException;
import com.example.erp.readmodel.domain.projection.DepartmentProjection;
import com.example.erp.readmodel.domain.projection.EmployeeProjection;
import com.example.erp.readmodel.domain.projection.repository.DepartmentProjectionRepository;
import com.example.erp.readmodel.domain.projection.repository.EmployeeProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QueryApprovalFactUseCase}: read-time subject resolution
 * (DEPARTMENT / EMPLOYEE / unresolved → null), the org_scope read filter
 * (DEPARTMENT subject + EMPLOYEE subject resolved-via-department), and the
 * out-of-scope detail → 404 rule. STRICT_STUBS.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class QueryApprovalFactUseCaseTest {

    @Mock ApprovalFactProjectionRepository approvalRepository;
    @Mock DepartmentProjectionRepository departmentRepository;
    @Mock EmployeeProjectionRepository employeeRepository;
    @Mock OrgViewMetricsPort metrics;

    @InjectMocks QueryApprovalFactUseCase useCase;

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @BeforeEach
    void setDepthBound() {
        ReflectionTestUtils.setField(useCase, "departmentPathMaxDepth", 32);
    }

    private ApprovalFactProjection departmentFact(String subjectDeptId) {
        return ApprovalFactProjection.ofSubmitted("appr-1", ApprovalSubjectType.DEPARTMENT,
                subjectDeptId, "emp-appr", "emp-sub", T0, T0, "evt-1");
    }

    private ApprovalFactProjection employeeFact(String subjectEmpId) {
        return ApprovalFactProjection.ofSubmitted("appr-2", ApprovalSubjectType.EMPLOYEE,
                subjectEmpId, "emp-appr", "emp-sub", T0, T0, "evt-2");
    }

    private DepartmentProjection dept(String id, String parentId) {
        return DepartmentProjection.of(id, id.toUpperCase(), id, parentId,
                MasterStatus.ACTIVE, null, null, T0, "d-evt");
    }

    private EmployeeProjection emp(String id, String deptId) {
        return EmployeeProjection.of(id, "E-" + id, id, deptId, null, null,
                MasterStatus.ACTIVE, null, null, T0, "e-evt");
    }

    @Test
    void getOneResolvesDepartmentSubjectWithPath() {
        when(approvalRepository.findById("appr-1")).thenReturn(Optional.of(departmentFact("dept-1")));
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept("dept-1", "hq")));
        when(departmentRepository.findById("hq")).thenReturn(Optional.of(dept("hq", null)));

        ApprovalFactView view = useCase.getOne("appr-1", null);

        assertThat(view.departmentSubject()).isNotNull();
        assertThat(view.departmentSubject().path()).hasSize(2);
        assertThat(view.departmentSubject().path().get(0).id()).isEqualTo("hq");
        assertThat(view.hasUnresolved()).isFalse();
    }

    @Test
    void getOneResolvesEmployeeSubjectTrimmed() {
        when(approvalRepository.findById("appr-2")).thenReturn(Optional.of(employeeFact("emp-1")));
        when(employeeRepository.findById("emp-1")).thenReturn(Optional.of(emp("emp-1", "dept-1")));

        ApprovalFactView view = useCase.getOne("appr-2", null);

        assertThat(view.employeeSubject()).isNotNull();
        assertThat(view.employeeSubject().id()).isEqualTo("emp-1");
        assertThat(view.hasUnresolved()).isFalse();
    }

    @Test
    void getOneUnresolvedSubjectYieldsNullPlusMetaUnresolved() {
        when(approvalRepository.findById("appr-1")).thenReturn(Optional.of(departmentFact("dept-x")));
        when(departmentRepository.findById("dept-x")).thenReturn(Optional.empty());

        ApprovalFactView view = useCase.getOne("appr-1", null);

        assertThat(view.departmentSubject()).isNull();
        assertThat(view.unresolved()).containsExactly("subject");
    }

    @Test
    void getOneProjectionMissThrowsNotFound() {
        when(approvalRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getOne("ghost", null))
                .isInstanceOf(ReadModelNotFoundException.class);
    }

    @Test
    void getOneOutOfScopeDepartmentSubjectThrowsNotFound() {
        // Subject dept-out is NOT under the scoped root dept-root → 404 (no leak).
        when(approvalRepository.findById("appr-1")).thenReturn(Optional.of(departmentFact("dept-out")));
        when(departmentRepository.findSubtreeIds("dept-root", 32))
                .thenReturn(List.of("dept-root", "dept-in"));

        assertThatThrownBy(() -> useCase.getOne("appr-1", List.of("dept-root")))
                .isInstanceOf(ReadModelNotFoundException.class);
    }

    @Test
    void getOneInScopeEmployeeSubjectResolvesViaDepartment() {
        when(approvalRepository.findById("appr-2")).thenReturn(Optional.of(employeeFact("emp-1")));
        when(departmentRepository.findSubtreeIds("dept-root", 32))
                .thenReturn(List.of("dept-root", "dept-in"));
        // The employee's department (dept-in) is in scope → allowed; then resolved for the ref.
        when(employeeRepository.findById("emp-1"))
                .thenReturn(Optional.of(emp("emp-1", "dept-in")));

        ApprovalFactView view = useCase.getOne("appr-2", List.of("dept-root"));

        assertThat(view.employeeSubject()).isNotNull();
        assertThat(view.employeeSubject().id()).isEqualTo("emp-1");
    }

    @Test
    void listUnboundedScopeBuildsNetZeroFilter() {
        when(approvalRepository.findPage(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(approvalRepository.count(any())).thenReturn(0L);

        useCase.list(ApprovalStatus.SUBMITTED, null, null, null, null, null, 0, 20);

        ArgumentCaptor<ApprovalFactFilter> captor = ArgumentCaptor.forClass(ApprovalFactFilter.class);
        org.mockito.Mockito.verify(approvalRepository).findPage(captor.capture(), eq(0), eq(20));
        assertThat(captor.getValue().scopeUnbounded()).isTrue();
        assertThat(captor.getValue().status()).isEqualTo(ApprovalStatus.SUBMITTED);
    }

    @Test
    void listBoundedScopeResolvesDepartmentAndEmployeeSubjectSets() {
        when(departmentRepository.findSubtreeIds("dept-root", 32))
                .thenReturn(List.of("dept-root", "dept-in"));
        when(employeeRepository.findIdsByDepartmentIdIn(List.of("dept-root", "dept-in")))
                .thenReturn(List.of("emp-1", "emp-2"));
        when(approvalRepository.findPage(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(approvalRepository.count(any())).thenReturn(0L);

        ApprovalFactPage page = useCase.list(null, null, null, null, null,
                List.of("dept-root"), 0, 20);

        ArgumentCaptor<ApprovalFactFilter> captor = ArgumentCaptor.forClass(ApprovalFactFilter.class);
        org.mockito.Mockito.verify(approvalRepository).findPage(captor.capture(), eq(0), eq(20));
        ApprovalFactFilter filter = captor.getValue();
        assertThat(filter.scopeUnbounded()).isFalse();
        assertThat(filter.scopedDepartmentIds()).containsExactlyInAnyOrder("dept-root", "dept-in");
        assertThat(filter.scopedEmployeeSubjectIds()).containsExactlyInAnyOrder("emp-1", "emp-2");
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void listResolvesSubjectsForReturnedFacts() {
        lenient().when(approvalRepository.count(any())).thenReturn(1L);
        when(approvalRepository.findPage(any(), anyInt(), anyInt()))
                .thenReturn(List.of(departmentFact("dept-1")));
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(dept("dept-1", null)));

        ApprovalFactPage page = useCase.list(null, null, null, null, null, null, 0, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().get(0).departmentSubject().id()).isEqualTo("dept-1");
    }
}
