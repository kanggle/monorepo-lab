package com.example.erp.masterdata.application;

import com.example.erp.masterdata.application.command.Commands.CreateDepartmentCommand;
import com.example.erp.masterdata.application.command.Commands.MoveDepartmentParentCommand;
import com.example.erp.masterdata.application.command.Commands.RetireDepartmentCommand;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.erp.masterdata.application.port.outbound.AuthorizationPort;
import com.example.erp.masterdata.application.port.outbound.ClockPort;
import com.example.erp.masterdata.application.view.DepartmentView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.erp.masterdata.domain.audit.AuditLog;
import com.example.erp.masterdata.domain.audit.AuditLogRepository;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerRepository;
import com.example.erp.masterdata.domain.common.PageResult;
import com.example.erp.masterdata.domain.costcenter.repository.CostCenterRepository;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.department.repository.DepartmentListFilter;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import com.example.erp.masterdata.domain.employee.repository.EmployeeRepository;
import com.example.erp.masterdata.domain.error.DomainErrors.DataScopeForbiddenException;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataDuplicateKeyException;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataParentCycleException;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataReferenceViolationException;
import com.example.erp.masterdata.domain.error.DomainErrors.PermissionDeniedException;
import com.example.erp.masterdata.domain.jobgrade.repository.JobGradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application unit tests for {@link MasterdataApplicationService} — proves
 * the single-Tx contract: authorization is checked BEFORE any repository
 * call (E6); audit_log row + event are written; reference-integrity + cycle
 * guards reject. {@code STRICT_STUBS}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class MasterdataApplicationServiceTest {

    private static final String TENANT = "erp";
    private static final Instant NOW = Instant.parse("2026-05-20T00:00:00Z");
    private static final ActorContext ACTOR = new ActorContext("user-1", TENANT,
            Set.of("erp.write", "erp.read"), Set.of("*"));

    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock JobGradeRepository jobGradeRepository;
    @Mock CostCenterRepository costCenterRepository;
    @Mock BusinessPartnerRepository businessPartnerRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock AuthorizationPort authorizationPort;
    @Mock ClockPort clock;
    @Mock MasterdataEventPublisher eventPublisher;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    MasterdataApplicationService service;

    @BeforeEach
    void stubDefaults() {
        lenient().when(clock.now()).thenReturn(NOW);
        lenient().when(departmentRepository.save(any(Department.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(auditLogRepository.append(any(AuditLog.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("E6: authorization checked BEFORE repository — DENY_ROLE → PermissionDenied")
    void authorizeBeforeRepo() {
        when(authorizationPort.evaluate(any(), any(), any()))
                .thenReturn(AuthorizationDecision.denyRole("no role"));

        assertThatThrownBy(() -> service.createDepartment(new CreateDepartmentCommand(
                ACTOR, "DEPT-1", "Sales", null, LocalDate.of(2026, 1, 1))))
                .isInstanceOf(PermissionDeniedException.class);

        // Repository was NEVER touched (single-path E6 — fail-CLOSED before any
        // master mutation).
        verify(departmentRepository, never()).save(any());
        verify(auditLogRepository, never()).append(any());
        verify(eventPublisher, never()).publishDepartmentChanged(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("E6: DENY_SCOPE → DataScopeForbidden, no repository call")
    void scopeDeny() {
        when(authorizationPort.evaluate(any(), any(), any()))
                .thenReturn(AuthorizationDecision.denyScope("out of scope"));

        assertThatThrownBy(() -> service.createDepartment(new CreateDepartmentCommand(
                ACTOR, "DEPT-1", "Sales", null, LocalDate.of(2026, 1, 1))))
                .isInstanceOf(DataScopeForbiddenException.class);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("E1: duplicate code on create → DuplicateKey, no save")
    void duplicateCodeRejected() {
        when(authorizationPort.evaluate(any(), any(), any()))
                .thenReturn(AuthorizationDecision.allow());
        Department existing = Department.create("d-9", TENANT, "DEPT-1", "Sales",
                null, EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        when(departmentRepository.findByCode("DEPT-1", TENANT))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createDepartment(new CreateDepartmentCommand(
                ACTOR, "DEPT-1", "Sales", null, LocalDate.of(2026, 1, 1))))
                .isInstanceOf(MasterdataDuplicateKeyException.class);
        verify(departmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("E2 / E8: createDepartment writes one audit row + one event in single Tx")
    void createWritesAuditAndEvent() {
        when(authorizationPort.evaluate(any(), any(), any()))
                .thenReturn(AuthorizationDecision.allow());
        when(departmentRepository.findByCode("DEPT-1", TENANT))
                .thenReturn(Optional.empty());

        service.createDepartment(new CreateDepartmentCommand(
                ACTOR, "DEPT-1", "Sales", null, LocalDate.of(2026, 1, 1)));

        verify(departmentRepository).save(any(Department.class));
        verify(auditLogRepository).append(any(AuditLog.class));
        verify(eventPublisher).publishDepartmentChanged(
                any(Department.class), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("E1: retire blocked by active child departments")
    void retireBlockedByChildren() {
        when(authorizationPort.evaluate(any(), any(), any()))
                .thenReturn(AuthorizationDecision.allow());
        Department parent = Department.create("d-1", TENANT, "DEPT-1", "Sales",
                null, EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        Department child = Department.create("d-2", TENANT, "DEPT-2", "Sales-EMEA",
                "d-1", EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        when(departmentRepository.findById("d-1", TENANT)).thenReturn(Optional.of(parent));
        when(departmentRepository.findActiveChildren("d-1", TENANT)).thenReturn(List.of(child));

        assertThatThrownBy(() -> service.retireDepartment(
                new RetireDepartmentCommand(ACTOR, "d-1", "merge")))
                .isInstanceOf(MasterdataReferenceViolationException.class);
        verify(auditLogRepository, never()).append(any());
    }

    @Test
    @DisplayName("E1: move-parent that would close a cycle is rejected")
    void moveParentCycleRejected() {
        when(authorizationPort.evaluate(any(), any(), any()))
                .thenReturn(AuthorizationDecision.allow());
        Department root = Department.create("d-1", TENANT, "DEPT-1", "Sales",
                null, EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        Department child = Department.create("d-2", TENANT, "DEPT-2", "Sales-EMEA",
                "d-1", EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        // attempt to move root under child → child's ancestry walks through root → cycle
        when(departmentRepository.findById("d-1", TENANT)).thenReturn(Optional.of(root));
        when(departmentRepository.findById("d-2", TENANT)).thenReturn(Optional.of(child));
        when(departmentRepository.ancestors("d-2", TENANT))
                .thenReturn(List.of(child, root));

        assertThatThrownBy(() -> service.moveDepartmentParent(
                new MoveDepartmentParentCommand(ACTOR, "d-1", "d-2",
                        LocalDate.of(2026, 6, 1), "reorg")))
                .isInstanceOf(MasterdataParentCycleException.class);
    }

    @Test
    @DisplayName("AC-1: list filter (asOf/active/parentId) is threaded to the repository unchanged")
    void listDepartmentsThreadsFilterToRepository() {
        when(authorizationPort.evaluate(any(), any(), any()))
                .thenReturn(AuthorizationDecision.allow());
        LocalDate asOf = LocalDate.of(2026, 3, 1);
        DepartmentListFilter filter = new DepartmentListFilter(asOf, Boolean.TRUE, "parent-9");
        when(departmentRepository.findAll(eq(TENANT), any(DepartmentListFilter.class), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0L));

        service.listDepartments(ACTOR, filter, 0, 20);

        ArgumentCaptor<DepartmentListFilter> captor = ArgumentCaptor.forClass(DepartmentListFilter.class);
        verify(departmentRepository).findAll(eq(TENANT), captor.capture(), eq(0), eq(20));
        DepartmentListFilter passed = captor.getValue();
        assertThat(passed.asOf()).isEqualTo(asOf);
        assertThat(passed.active()).isTrue();
        assertThat(passed.parentId()).isEqualTo("parent-9");
    }

    @Test
    @DisplayName("AC-2: list returns the repository's TRUE total, not the page-content size")
    void listDepartmentsReturnsTrueTotalNotPageSize() {
        when(authorizationPort.evaluate(any(), any(), any()))
                .thenReturn(AuthorizationDecision.allow());
        // Page slice holds 2 rows, but the query total across all pages is 25.
        Department d1 = Department.create("d-1", TENANT, "DEPT-1", "Sales", null,
                EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        Department d2 = Department.create("d-2", TENANT, "DEPT-2", "Ops", null,
                EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        when(departmentRepository.findAll(eq(TENANT), any(DepartmentListFilter.class), eq(0), eq(2)))
                .thenReturn(new PageResult<>(List.of(d1, d2), 25L));

        PageResult<DepartmentView> result =
                service.listDepartments(ACTOR, DepartmentListFilter.unfiltered(), 0, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(25L);
        assertThat(result.totalElements()).isNotEqualTo((long) result.content().size());
    }
}
