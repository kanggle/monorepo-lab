package com.example.erp.masterdata.application;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.erp.masterdata.application.port.outbound.ClockPort;
import com.example.erp.masterdata.domain.audit.AuditLogRepository;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerRepository;
import com.example.erp.masterdata.domain.costcenter.repository.CostCenterRepository;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.employee.link.repository.EmployeeAccountLinkProposalRepository;
import com.example.erp.masterdata.domain.employee.repository.EmployeeRepository;
import com.example.erp.masterdata.domain.error.DomainErrors.DataScopeForbiddenException;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * TASK-MONO-793 (ADR-MONO-083 P6 · owner decisions Q2 · Q3, 2026-10-15 UTC) — which masterdata use cases are
 * «personal» and which are the approval subject check, run through the REAL {@link RoleScopeAuthorizationAdapter}
 * (repositories mocked). The use case → requirement mapping lives in the application service, so a test with a
 * mocked port could not notice a personal endpoint quietly asking for the masterdata read role.
 *
 * <ul>
 *   <li>Q2 personal ({@code /employees/me}, {@code approver-ref}): any ERP business role (or the derived entry
 *       role) — a person holding only approval roles can still be resolved as an employee (else they could never
 *       submit or approve);</li>
 *   <li>Q3 subject check ({@code GET /departments/{id}} · {@code GET /employees/{id}}): masterdata read OR
 *       {@code ERP_APPROVAL_WRITE} — approval forwards the operator's token here at submit;</li>
 *   <li>every other read: masterdata read only — an approval holder's department LIST is 403;</li>
 *   <li>a subscription alone ({@code entitled_domains ∋ erp}) opens none of them (Q1);</li>
 *   <li>the department-scope check runs unchanged after the role check (AC-2).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MasterdataPersonalAndSubjectGateTest {

    private static final String TENANT = "demo-corp";
    private static final Instant NOW = Instant.parse("2026-10-15T00:00:00Z");
    private static final Set<String> ALL = Set.of("*");

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

    private final Employee employee = Employee.create("emp-1", TENANT, "E-1", "Kim", "dept-sales",
            "cc-1", "jg-1", EffectivePeriod.openEnded(LocalDate.of(2020, 1, 1)), NOW);
    private final Department department = Department.create("dept-sales", TENANT, "SALES", "Sales",
            "dept-root", EffectivePeriod.openEnded(LocalDate.of(2020, 1, 1)), NOW);

    @BeforeEach
    void wire() {
        service = new MasterdataApplicationService(departmentRepository, employeeRepository, jobGradeRepository,
                costCenterRepository, businessPartnerRepository, auditLogRepository,
                new RoleScopeAuthorizationAdapter(departmentRepository), clock, eventPublisher, objectMapper,
                linkProposalRepository);
        lenient().when(employeeRepository.findByAccountId(anyString(), anyString())).thenReturn(Optional.of(employee));
        lenient().when(employeeRepository.findById(anyString(), anyString())).thenReturn(Optional.of(employee));
        lenient().when(departmentRepository.findById(anyString(), anyString())).thenReturn(Optional.of(department));
        lenient().when(departmentRepository.findAll(anyString(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));
    }

    private static ActorContext actor(Set<String> roles) {
        return new ActorContext("acct-1", TENANT, roles, ALL, Set.of("erp"));
    }

    // ---- Q2 personal ---------------------------------------------------------------------------------

    @Test
    @DisplayName("🔴 793 Q2: approval roles only (no entry role on the wire) → /employees/me resolves")
    void me_approvalRoleOnly_allowed() {
        assertThat(service.getMyEmployee(actor(Set.of("ERP_APPROVAL_WRITE"))).id()).isEqualTo("emp-1");
    }

    @Test
    @DisplayName("🔴 793 Q2: masterdata read role only → /employees/me resolves")
    void me_masterdataReadOnly_allowed() {
        assertThat(service.getMyEmployee(actor(Set.of("ERP_MASTERDATA_READ"))).id()).isEqualTo("emp-1");
    }

    @Test
    @DisplayName("793 Q2: the derived entry role → /employees/me resolves (it is what every ERP holder carries)")
    void me_entryRole_allowed() {
        assertThat(service.getMyEmployee(actor(Set.of("ERP_OPERATOR"))).id()).isEqualTo("emp-1");
    }

    @Test
    @DisplayName("🔴 793 Q1/Q2: subscription only (entitled_domains ∋ erp, no role) → /employees/me 403")
    void me_subscriptionOnly_refused() {
        assertThatThrownBy(() -> service.getMyEmployee(actor(Set.of())))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("🔴 793 Q3: approval roles only → approver-ref resolves (approval's stage-approver lookup)")
    void approverRef_approvalRoleOnly_allowed() {
        assertThat(service.getApproverRef("emp-1", actor(Set.of("ERP_APPROVAL_WRITE"))).id()).isEqualTo("emp-1");
    }

    // ---- Q3 subject check ----------------------------------------------------------------------------

    @Test
    @DisplayName("🔴 793 Q3: ERP_APPROVAL_WRITE → GET /departments/{id} and /employees/{id} (subject check) allowed")
    void subject_approvalWrite_allowed() {
        ActorContext approver = actor(Set.of("ERP_APPROVAL_WRITE"));
        assertThat(service.getDepartment("dept-sales", approver, null).id()).isEqualTo("dept-sales");
        assertThat(service.getEmployee("emp-1", approver, null).id()).isEqualTo("emp-1");
    }

    @Test
    @DisplayName("🔴 793 Q3: approval READ only (+ entry role) → the subject GETs are 403 (only the writer submits)")
    void subject_approvalReadOnly_refused() {
        ActorContext reader = actor(Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ"));
        assertThatThrownBy(() -> service.getDepartment("dept-sales", reader, null))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.getEmployee("emp-1", reader, null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("🔴 793 AC-1: approval set → the department LIST (a masterdata read) is 403")
    void list_approvalSet_refused() {
        ActorContext approver = actor(Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE"));
        assertThatThrownBy(() -> service.listDepartments(approver, null, 0, 20))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    @DisplayName("793 AC-2: the department-scope check still runs after the role check (org_scope outside → 403 scope)")
    void subject_orgScopeUnchanged() {
        lenient().when(departmentRepository.ancestors(anyString(), anyString())).thenReturn(List.of());
        ActorContext narrow = new ActorContext("acct-1", TENANT, Set.of("ERP_OPERATOR", "ERP_APPROVAL_WRITE"),
                Set.of("dept-hr"), Set.of());
        assertThatThrownBy(() -> service.getDepartment("dept-sales", narrow, null))
                .isInstanceOf(DataScopeForbiddenException.class);
    }

    @Test
    @DisplayName("793 AC-2 control: legacy-compat token → every one of these reads allowed")
    void legacy_allAllowed() {
        ActorContext legacy = actor(Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ", "ERP_MASTERDATA_WRITE",
                "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE"));
        assertThat(service.getMyEmployee(legacy).id()).isEqualTo("emp-1");
        assertThat(service.getApproverRef("emp-1", legacy).id()).isEqualTo("emp-1");
        assertThat(service.getDepartment("dept-sales", legacy, null).id()).isEqualTo("dept-sales");
        assertThat(service.getEmployee("emp-1", legacy, null).id()).isEqualTo("emp-1");
        assertThat(service.listDepartments(legacy, null, 0, 20).content()).isEmpty();
    }
}
