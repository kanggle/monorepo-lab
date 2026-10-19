package com.example.erp.masterdata.application;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.erp.masterdata.application.port.outbound.ClockPort;
import com.example.erp.masterdata.application.view.EmployeePickerResult;
import com.example.erp.masterdata.application.view.EmployeePickerView;
import com.example.erp.masterdata.domain.audit.AuditLogRepository;
import com.example.erp.masterdata.domain.authorization.ListDataScope;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerRepository;
import com.example.erp.masterdata.domain.costcenter.repository.CostCenterRepository;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.employee.link.repository.EmployeeAccountLinkProposalRepository;
import com.example.erp.masterdata.domain.employee.repository.EmployeeRepository;
import com.example.erp.masterdata.domain.error.DomainErrors.PermissionDeniedException;
import com.example.erp.masterdata.domain.jobgrade.repository.JobGradeRepository;
import com.example.erp.masterdata.infrastructure.authorization.RoleScopeAuthorizationAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-ERP-BE-045 — {@code GET /employees/picker} through the REAL {@link RoleScopeAuthorizationAdapter}
 * (repositories mocked), so the use case → requirement mapping and the scope resolution are both exercised:
 * a test with a mocked port could not notice the picker quietly asking for {@code READ} (approval writer 403)
 * or quietly skipping the subtree scope (Failure Scenario 3).
 *
 * <p>Control group (AC-2): no role 403 · {@code ERP_APPROVAL_READ} only 403 · {@code ERP_APPROVAL_WRITE} 200 ·
 * {@code ERP_MASTERDATA_READ} 200. Scope: {@code '*'} = whole tenant · root = that subtree · none = empty page.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class EmployeePickerServiceTest {

    private static final String TENANT = "demo-corp";
    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final LocalDate FROM = LocalDate.of(2020, 1, 1);

    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock JobGradeRepository jobGradeRepository;
    @Mock CostCenterRepository costCenterRepository;
    @Mock BusinessPartnerRepository businessPartnerRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ClockPort clock;
    @Mock MasterdataEventPublisher eventPublisher;
    @Mock ObjectMapper objectMapper;
    @Mock EmployeeAccountLinkProposalRepository linkProposalRepository;

    private MasterdataApplicationService service;

    private final Employee kim = Employee.create("emp-1", TENANT, "E-1001", "Kim", "dept-sales",
            "cc-1", "jg-1", EffectivePeriod.openEnded(FROM), NOW);
    private final Employee lee = Employee.create("emp-2", TENANT, "E-1002", "Lee", "dept-gone",
            "cc-1", "jg-1", EffectivePeriod.openEnded(FROM), NOW);
    private final Department sales = Department.create("dept-sales", TENANT, "SALES", "영업본부",
            "dept-root", EffectivePeriod.openEnded(FROM), NOW);
    /** A department whose period ended before today — its name must NOT resolve (contract: ABSENT). */
    private final Department gone = Department.create("dept-gone", TENANT, "GONE", "폐지부서",
            "dept-root", EffectivePeriod.closed(FROM, LocalDate.of(2025, 1, 1)), NOW);

    @BeforeEach
    void wire() {
        service = new MasterdataApplicationService(departmentRepository, employeeRepository, jobGradeRepository,
                costCenterRepository, businessPartnerRepository, auditLogRepository,
                new RoleScopeAuthorizationAdapter(departmentRepository), clock, eventPublisher, objectMapper,
                linkProposalRepository);
    }

    private static ActorContext actor(Set<String> roles, Set<String> orgScope) {
        return new ActorContext("acct-1", TENANT, roles, orgScope, Set.of("erp"));
    }

    private void stubTenantPage() {
        when(employeeRepository.findPickerPage(eq(TENANT), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(kim), 0, 20, 1L, 1));
        when(clock.now()).thenReturn(NOW);
        when(departmentRepository.findAllByIds(anyCollection(), eq(TENANT))).thenReturn(List.of(sales));
    }

    // ---- AC-2 control group ------------------------------------------------------------------------

    @Test
    @DisplayName("🔴 BE-045 AC-2: no role (subscription only) → 403 PERMISSION_DENIED, no repository touched")
    void noRole_refused() {
        assertThatThrownBy(() -> service.listEmployeePicker(actor(Set.of(), Set.of("*")), null, 0, 20))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(employeeRepository, departmentRepository);
    }

    @Test
    @DisplayName("🔴 BE-045 AC-2: ERP_APPROVAL_READ (+ entry role) only → 403 PERMISSION_DENIED")
    void approvalReadOnly_refused() {
        assertThatThrownBy(() -> service.listEmployeePicker(
                actor(Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ"), Set.of("*")), null, 0, 20))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(employeeRepository, departmentRepository);
    }

    @Test
    @DisplayName("🔴 BE-045 AC-2: the «결재 운영» set (ERP_APPROVAL_READ + ERP_APPROVAL_WRITE) → 200")
    void approvalWrite_allowed() {
        stubTenantPage();
        EmployeePickerResult r = service.listEmployeePicker(
                actor(Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE"), Set.of("*")), null, 0, 20);
        assertThat(r.page().content()).extracting(EmployeePickerView::id).containsExactly("emp-1");
    }

    @Test
    @DisplayName("BE-045 AC-2: ERP_MASTERDATA_READ → 200")
    void masterdataRead_allowed() {
        stubTenantPage();
        EmployeePickerResult r = service.listEmployeePicker(
                actor(Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ"), Set.of("*")), null, 0, 20);
        assertThat(r.page().content()).hasSize(1);
    }

    @Test
    @DisplayName("BE-045 control: the existing employee LIST still refuses the approval writer (picker is the only new door)")
    void existingList_stillRefusesApprovalWriter() {
        assertThatThrownBy(() -> service.listEmployees(
                actor(Set.of("ERP_OPERATOR", "ERP_APPROVAL_WRITE"), Set.of("*")), null, 0, 20))
                .isInstanceOf(PermissionDeniedException.class);
    }

    // ---- data scope ---------------------------------------------------------------------------------

    @Test
    @DisplayName("BE-045 scope: org_scope ∋ '*' → whole tenant (departmentIds = null), scope TENANT")
    void platformScope_tenantWide() {
        stubTenantPage();
        EmployeePickerResult r = service.listEmployeePicker(
                actor(Set.of("ERP_APPROVAL_WRITE"), Set.of("*")), null, 0, 20);
        assertThat(r.scope()).isEqualTo(ListDataScope.Kind.TENANT);
        verify(departmentRepository, never()).subtreeIds(any(), anyString());
    }

    @Test
    @DisplayName("🔴 BE-045 scope: root A → the query is restricted to A's expanded subtree, scope SUBTREE")
    void rootScope_subtreeOnly() {
        when(departmentRepository.subtreeIds(Set.of("dept-A"), TENANT))
                .thenReturn(Set.of("dept-A", "dept-A1", "dept-A11"));
        when(employeeRepository.findPickerPage(eq(TENANT), eq(Boolean.TRUE),
                eq(Set.of("dept-A", "dept-A1", "dept-A11")), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        EmployeePickerResult r = service.listEmployeePicker(
                actor(Set.of("ERP_APPROVAL_WRITE"), Set.of("dept-A")), Boolean.TRUE, 0, 20);

        assertThat(r.scope()).isEqualTo(ListDataScope.Kind.SUBTREE);
        assertThat(r.page().totalElements()).isZero();
    }

    @Test
    @DisplayName("🔴 BE-045 scope: empty org_scope → 200 empty page (fail-closed), scope SUBTREE, no employee query")
    void emptyScope_emptyPage() {
        EmployeePickerResult r = service.listEmployeePicker(
                actor(Set.of("ERP_APPROVAL_WRITE"), Set.of()), null, 0, 20);
        assertThat(r.page().content()).isEmpty();
        assertThat(r.page().totalElements()).isZero();
        assertThat(r.page().totalPages()).isZero();
        assertThat(r.scope()).isEqualTo(ListDataScope.Kind.SUBTREE);
        verify(employeeRepository, never()).findPickerPage(anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("BE-045 scope: absent org_scope (null) → 200 empty page, scope SUBTREE")
    void absentScope_emptyPage() {
        EmployeePickerResult r = service.listEmployeePicker(
                actor(Set.of("ERP_MASTERDATA_READ"), null), null, 0, 20);
        assertThat(r.page().content()).isEmpty();
        assertThat(r.scope()).isEqualTo(ListDataScope.Kind.SUBTREE);
        verifyNoInteractions(employeeRepository);
    }

    // ---- element shape --------------------------------------------------------------------------------

    @Test
    @DisplayName("BE-045 element: departmentName resolves only for a department effective today; accountLinked reflects accountId")
    void elementValues() {
        kim.linkAccount("acct-kim", NOW);
        when(employeeRepository.findPickerPage(eq(TENANT), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(kim, lee), 0, 20, 2L, 1));
        when(clock.now()).thenReturn(NOW);
        when(departmentRepository.findAllByIds(anyCollection(), eq(TENANT))).thenReturn(List.of(sales, gone));

        List<EmployeePickerView> rows = service.listEmployeePicker(
                actor(Set.of("ERP_APPROVAL_WRITE"), Set.of("*")), null, 0, 20).page().content();

        assertThat(rows.get(0)).isEqualTo(new EmployeePickerView("emp-1", "E-1001", "Kim", "dept-sales",
                "영업본부", "ACTIVE", true));
        assertThat(rows.get(1)).isEqualTo(new EmployeePickerView("emp-2", "E-1002", "Lee", "dept-gone",
                null, "ACTIVE", false));
    }

    @Test
    @DisplayName("BE-045 element: 'today' is the UTC date — a department starting tomorrow (UTC) does not resolve yet")
    void departmentNameUsesUtcToday() {
        Department startsTomorrow = Department.create("dept-sales", TENANT, "SALES", "영업본부",
                null, EffectivePeriod.openEnded(TODAY.plusDays(1)), NOW);
        when(employeeRepository.findPickerPage(eq(TENANT), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(kim), 0, 20, 1L, 1));
        when(clock.now()).thenReturn(NOW);
        when(departmentRepository.findAllByIds(anyCollection(), eq(TENANT))).thenReturn(List.of(startsTomorrow));

        assertThat(service.listEmployeePicker(actor(Set.of("ERP_APPROVAL_WRITE"), Set.of("*")), null, 0, 20)
                .page().content().get(0).departmentName()).isNull();
    }
}
