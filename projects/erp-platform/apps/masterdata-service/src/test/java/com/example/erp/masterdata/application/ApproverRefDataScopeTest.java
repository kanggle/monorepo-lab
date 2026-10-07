package com.example.erp.masterdata.application;

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
import com.example.erp.masterdata.domain.jobgrade.repository.JobGradeRepository;
import com.example.erp.masterdata.infrastructure.authorization.RoleScopeAuthorizationAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.when;

/**
 * AC-6 / Failure Scenario 2 (TASK-ERP-BE-044) against the REAL {@link RoleScopeAuthorizationAdapter}
 * — not a mocked port, because the defect this pins is precisely «which target is passed to the
 * real data-scope check». One out-of-scope operator, one employee, two reads, in one test:
 * {@code getEmployee} (department-scoped) is forbidden, {@code getApproverRef} and
 * {@code getMyEmployee} (no department scope, by contract) succeed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApproverRefDataScopeTest {

    private static final String TENANT = "erp";
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");

    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock JobGradeRepository jobGradeRepository;
    @Mock CostCenterRepository costCenterRepository;
    @Mock BusinessPartnerRepository businessPartnerRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock ClockPort clock;
    @Mock MasterdataEventPublisher eventPublisher;
    @Mock EmployeeAccountLinkProposalRepository linkProposalRepository;

    @Test
    @DisplayName("AC-6: out-of-scope operator — GET /employees/{id} → DATA_SCOPE_FORBIDDEN, /approver-ref and /me → OK")
    void approverRefIgnoresDepartmentScope() {
        MasterdataApplicationService service = new MasterdataApplicationService(departmentRepository,
                employeeRepository, jobGradeRepository, costCenterRepository, businessPartnerRepository,
                auditLogRepository, new RoleScopeAuthorizationAdapter(departmentRepository), clock,
                eventPublisher, new ObjectMapper(), linkProposalRepository);

        // The approver sits in the CFO office; the submitter's operator scope is only "sales".
        Employee approver = Employee.create("emp-cfo", TENANT, "E-CFO", "CFO", "dept-cfo", "cc-1", "jg-1",
                EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        approver.linkAccount("acct-cfo", NOW);
        Department cfoOffice = Department.create("dept-cfo", TENANT, "CFO", "CFO office", null,
                EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
        when(employeeRepository.findById("emp-cfo", TENANT)).thenReturn(Optional.of(approver));
        when(departmentRepository.ancestors("dept-cfo", TENANT)).thenReturn(List.of(cfoOffice));
        when(employeeRepository.findByAccountId("acct-cfo", TENANT)).thenReturn(Optional.of(approver));

        ActorContext salesOperator = new ActorContext("op-sales", TENANT, Set.of("erp.read"), Set.of("dept-sales"));

        assertThatThrownBy(() -> service.getEmployee("emp-cfo", salesOperator, null))
                .isInstanceOf(DataScopeForbiddenException.class);

        assertThat(service.getApproverRef("emp-cfo", salesOperator).accountId()).isEqualTo("acct-cfo");

        // The CFO's own account (also outside "dept-sales"-like scopes — here no org_scope at all)
        // resolves itself through /me.
        ActorContext cfoSelf = new ActorContext("acct-cfo", TENANT, Set.of("erp.read"), Set.of());
        assertThat(service.getMyEmployee(cfoSelf).id()).isEqualTo("emp-cfo");
    }
}
