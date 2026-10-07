package com.example.erp.masterdata.application;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.application.command.Commands.AcceptAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.DeclineAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.ProposeAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.RevokeAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.UnlinkAccountCommand;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher.ChangeKind;
import com.example.erp.masterdata.application.port.outbound.AuthorizationPort;
import com.example.erp.masterdata.application.port.outbound.ClockPort;
import com.example.erp.masterdata.application.view.EmployeeAccountLinkProposalView;
import com.example.erp.masterdata.application.view.EmployeeApproverRefView;
import com.example.erp.masterdata.application.view.EmployeeView;
import com.example.erp.masterdata.domain.audit.AuditLog;
import com.example.erp.masterdata.domain.audit.AuditLogRepository;
import com.example.erp.masterdata.domain.authorization.AuthorizationDecision;
import com.example.erp.masterdata.domain.authorization.RequiredScope;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerRepository;
import com.example.erp.masterdata.domain.costcenter.repository.CostCenterRepository;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.employee.link.EmployeeAccountLinkProposal;
import com.example.erp.masterdata.domain.employee.link.LinkProposalStatus;
import com.example.erp.masterdata.domain.employee.link.repository.EmployeeAccountLinkProposalRepository;
import com.example.erp.masterdata.domain.employee.repository.EmployeeRepository;
import com.example.erp.masterdata.domain.error.DomainErrors.DataScopeForbiddenException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkConflictException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkInvalidException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkNotAddresseeException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkProposalNotFoundException;
import com.example.erp.masterdata.domain.error.DomainErrors.EmployeeLinkSelfAcceptException;
import com.example.erp.masterdata.domain.error.DomainErrors.MasterdataNotFoundException;
import com.example.erp.masterdata.domain.error.DomainErrors.PermissionDeniedException;
import com.example.erp.masterdata.domain.jobgrade.repository.JobGradeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application unit tests for the employee ↔ IAM account link use cases (TASK-ERP-BE-044) on
 * {@link MasterdataApplicationService}. {@code STRICT_STUBS}; the {@link ObjectMapper} is a real
 * spy so the audit JSON can be asserted.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class EmployeeAccountLinkServiceTest {

    private static final String TENANT = "erp";
    private static final Instant NOW = Instant.parse("2026-10-08T00:00:00Z");
    /** HR operator — proposes / revokes. */
    private static final ActorContext HR = new ActorContext("hr-1", TENANT,
            Set.of("erp.write", "erp.read"), Set.of("*"));
    /** The account owner — accepts / declines. Read-only participant. */
    private static final ActorContext OWNER = new ActorContext("acct-A", TENANT,
            Set.of("erp.read"), Set.of());

    @Mock DepartmentRepository departmentRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock JobGradeRepository jobGradeRepository;
    @Mock CostCenterRepository costCenterRepository;
    @Mock BusinessPartnerRepository businessPartnerRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock AuthorizationPort authorizationPort;
    @Mock ClockPort clock;
    @Mock MasterdataEventPublisher eventPublisher;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @Mock EmployeeAccountLinkProposalRepository linkProposalRepository;

    @InjectMocks
    MasterdataApplicationService service;

    @BeforeEach
    void stubDefaults() {
        lenient().when(clock.now()).thenReturn(NOW);
        lenient().when(auditLogRepository.append(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(authorizationPort.evaluate(any(), any(), any())).thenReturn(AuthorizationDecision.allow());
        lenient().when(employeeRepository.saveAccountLink(any(Employee.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(linkProposalRepository.save(any(EmployeeAccountLinkProposal.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(linkProposalRepository.insertPending(any(EmployeeAccountLinkProposal.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private static Employee activeEmployee() {
        return Employee.create("emp-1", TENANT, "E-1", "Hong", "dept-1", "cc-1", "jg-1",
                EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
    }

    private static EmployeeAccountLinkProposal pending(String accountId, String proposedBy) {
        return EmployeeAccountLinkProposal.propose("p-1", TENANT, "emp-1", accountId, proposedBy, null, NOW);
    }

    private AuditLog onlyAuditRow() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).append(captor.capture());
        return captor.getValue();
    }

    // =====================================================================
    @Nested
    @DisplayName("propose")
    class Propose {

        @Test
        @DisplayName("happy: PENDING proposal inserted (DB-unique path), one audit row, NO employee event")
        void happy() {
            Employee e = activeEmployee();
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(e));
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.empty());
            when(linkProposalRepository.findPendingByEmployeeId("emp-1", TENANT)).thenReturn(Optional.empty());

            EmployeeAccountLinkProposalView v = service.proposeAccountLink(
                    new ProposeAccountLinkCommand(HR, "emp-1", "acct-A", "onboarding"));

            assertThat(v.status()).isEqualTo("PENDING");
            assertThat(v.accountId()).isEqualTo("acct-A");
            assertThat(v.proposedBy()).isEqualTo("hr-1");
            assertThat(v.reason()).isEqualTo("onboarding");
            verify(linkProposalRepository).insertPending(any(EmployeeAccountLinkProposal.class));
            AuditLog row = onlyAuditRow();
            assertThat(row.getAggregateType()).isEqualTo("employee_account_link_proposal");
            assertThat(row.getAction()).isEqualTo("PROPOSE_ACCOUNT_LINK");
            assertThat(row.getActor()).isEqualTo("hr-1");
            // A proposal does not change the employee — no employee.changed.
            verify(eventPublisher, never()).publishEmployeeChanged(any(), any(), any(), any(), any(), any());
            // Employee row untouched: accountId is written ONLY by accept.
            assertThat(e.getAccountId()).isNull();
            verify(employeeRepository, never()).saveAccountLink(any());
        }

        @Test
        @DisplayName("auth: role gate first (target null), then the employee's department scope (WRITE)")
        void authorizesWriteWithDepartmentScope() {
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.empty());
            when(linkProposalRepository.findPendingByEmployeeId("emp-1", TENANT)).thenReturn(Optional.empty());

            service.proposeAccountLink(new ProposeAccountLinkCommand(HR, "emp-1", "acct-A", null));

            verify(authorizationPort).evaluate(HR, RequiredScope.WRITE, null);
            verify(authorizationPort).evaluate(HR, RequiredScope.WRITE, "dept-1");
        }

        @Test
        @DisplayName("role gate denies before any repository read")
        void roleDeniedBeforeRepository() {
            when(authorizationPort.evaluate(HR, RequiredScope.WRITE, null))
                    .thenReturn(AuthorizationDecision.denyRole("no write"));

            assertThatThrownBy(() -> service.proposeAccountLink(
                    new ProposeAccountLinkCommand(HR, "emp-1", "acct-A", null)))
                    .isInstanceOf(PermissionDeniedException.class);
            verify(employeeRepository, never()).findById(anyString(), anyString());
        }

        @Test
        @DisplayName("unknown employee → 404 MASTERDATA_NOT_FOUND")
        void unknownEmployee() {
            when(employeeRepository.findById("emp-x", TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.proposeAccountLink(
                    new ProposeAccountLinkCommand(HR, "emp-x", "acct-A", null)))
                    .isInstanceOf(MasterdataNotFoundException.class);
        }

        @Test
        @DisplayName("AC-5: RETIRED employee → 422 EMPLOYEE_LINK_INVALID employee_not_active")
        void retiredEmployee() {
            Employee e = activeEmployee();
            e.retire(NOW);
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(e));

            assertThatThrownBy(() -> service.proposeAccountLink(
                    new ProposeAccountLinkCommand(HR, "emp-1", "acct-A", null)))
                    .isInstanceOfSatisfying(EmployeeLinkInvalidException.class, ex ->
                            assertThat(ex.details()).containsEntry("cause", "employee_not_active"));
            verify(linkProposalRepository, never()).insertPending(any());
        }

        @Test
        @DisplayName("two-person rule, early: proposing the proposer's own account → 403 SELF_ACCEPT")
        void ownAccount() {
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));

            assertThatThrownBy(() -> service.proposeAccountLink(
                    new ProposeAccountLinkCommand(HR, "emp-1", "hr-1", null)))
                    .isInstanceOf(EmployeeLinkSelfAcceptException.class);
            verify(linkProposalRepository, never()).insertPending(any());
        }

        @Test
        @DisplayName("employee already linked → 409 employee_already_linked")
        void employeeAlreadyLinked() {
            Employee e = activeEmployee();
            e.linkAccount("acct-Z", NOW);
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(e));

            assertConflict(() -> service.proposeAccountLink(
                    new ProposeAccountLinkCommand(HR, "emp-1", "acct-A", null)), "employee_already_linked");
        }

        @Test
        @DisplayName("AC-4: account already linked to another employee in this tenant → 409 account_already_linked")
        void accountAlreadyLinked() {
            Employee other = Employee.create("emp-2", TENANT, "E-2", "Kim", "dept-1", "cc-1", "jg-1",
                    EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
            other.linkAccount("acct-A", NOW);
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.of(other));

            assertConflict(() -> service.proposeAccountLink(
                    new ProposeAccountLinkCommand(HR, "emp-1", "acct-A", null)), "account_already_linked");
        }

        @Test
        @DisplayName("AC-4: a second PENDING proposal for the employee → 409 proposal_pending (application pre-check)")
        void secondPending() {
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));
            when(employeeRepository.findByAccountId("acct-B", TENANT)).thenReturn(Optional.empty());
            when(linkProposalRepository.findPendingByEmployeeId("emp-1", TENANT))
                    .thenReturn(Optional.of(pending("acct-A", "hr-2")));

            assertConflict(() -> service.proposeAccountLink(
                    new ProposeAccountLinkCommand(HR, "emp-1", "acct-B", null)), "proposal_pending");
            verify(linkProposalRepository, never()).insertPending(any());
        }

        @Test
        @DisplayName("AC-4: the DB-unique translation from insertPending surfaces as 409 and writes no audit")
        void dbUniqueOnInsert() {
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.empty());
            when(linkProposalRepository.findPendingByEmployeeId("emp-1", TENANT)).thenReturn(Optional.empty());
            when(linkProposalRepository.insertPending(any(EmployeeAccountLinkProposal.class)))
                    .thenThrow(new EmployeeLinkConflictException("proposal_pending", "race"));

            assertConflict(() -> service.proposeAccountLink(
                    new ProposeAccountLinkCommand(HR, "emp-1", "acct-A", null)), "proposal_pending");
            verify(auditLogRepository, never()).append(any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("accept")
    class Accept {

        @Test
        @DisplayName("AC-1 happy: writes employees.account_id, proposal ACCEPTED, one audit row, one employee.changed UPDATED")
        void happy() {
            Employee e = activeEmployee();
            EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(p));
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(e));
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.empty());

            EmployeeView v = service.acceptAccountLink(new AcceptAccountLinkCommand(OWNER, "p-1"));

            assertThat(v.accountId()).isEqualTo("acct-A");
            assertThat(e.getAccountId()).isEqualTo("acct-A");
            assertThat(p.getStatus()).isEqualTo(LinkProposalStatus.ACCEPTED);
            assertThat(p.getDecidedBy()).isEqualTo("acct-A");
            verify(employeeRepository).saveAccountLink(e);
            verify(linkProposalRepository).save(p);

            AuditLog row = onlyAuditRow();
            assertThat(row.getAggregateType()).isEqualTo("employee");
            assertThat(row.getAction()).isEqualTo("ACCEPT_ACCOUNT_LINK");
            assertThat(row.getActor()).isEqualTo("acct-A");
            assertThat(row.getAfterState()).contains("\"accountId\":\"acct-A\"").contains("\"proposalId\":\"p-1\"");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
            verify(eventPublisher).publishEmployeeChanged(eq(e), eq(ChangeKind.UPDATED), eq("acct-A"),
                    any(), after.capture(), isNull());
            assertThat(after.getValue()).containsEntry("accountId", "acct-A");
        }

        @Test
        @DisplayName("no department scope: only the role gate (READ, null) is evaluated")
        void noDepartmentScope() {
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(pending("acct-A", "hr-1")));
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.empty());

            service.acceptAccountLink(new AcceptAccountLinkCommand(OWNER, "p-1"));

            verify(authorizationPort).evaluate(OWNER, RequiredScope.READ, null);
            verify(authorizationPort, never()).evaluate(any(), any(), eq("dept-1"));
        }

        @Test
        @DisplayName("unknown proposal → 404 EMPLOYEE_LINK_PROPOSAL_NOT_FOUND")
        void unknownProposal() {
            when(linkProposalRepository.findById("p-x", TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.acceptAccountLink(new AcceptAccountLinkCommand(OWNER, "p-x")))
                    .isInstanceOf(EmployeeLinkProposalNotFoundException.class);
        }

        /**
         * 🔴 AC-2 bite target. The row is self-addressed (proposer == account) — it bypassed the
         * proposal-time check (here: built directly, as a direct DB insert would be). Only the
         * accept-side two-person rule stands between it and {@code employees.account_id}.
         */
        @Test
        @DisplayName("🔴 AC-2 two-person rule: proposer == acceptor on a row that bypassed the proposal check → 403 SELF_ACCEPT, account_id unchanged")
        void selfAcceptRejected() {
            Employee e = activeEmployee();
            ActorContext selfProposer = new ActorContext("acct-A", TENANT, Set.of("erp.write", "erp.read"), Set.of("*"));
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(pending("acct-A", "acct-A")));
            lenient().when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(e));
            lenient().when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.acceptAccountLink(new AcceptAccountLinkCommand(selfProposer, "p-1")))
                    .isInstanceOf(EmployeeLinkSelfAcceptException.class);

            assertThat(e.getAccountId()).isNull();
            verify(employeeRepository, never()).saveAccountLink(any());
            verify(linkProposalRepository, never()).save(any());
            verify(auditLogRepository, never()).append(any());
            verify(eventPublisher, never()).publishEmployeeChanged(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("AC-3: caller is not the addressee → 403 NOT_ADDRESSEE")
        void notAddressee() {
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(pending("acct-A", "hr-1")));
            ActorContext stranger = new ActorContext("acct-Z", TENANT, Set.of("erp.read"), Set.of());

            assertThatThrownBy(() -> service.acceptAccountLink(new AcceptAccountLinkCommand(stranger, "p-1")))
                    .isInstanceOf(EmployeeLinkNotAddresseeException.class);
            verify(employeeRepository, never()).saveAccountLink(any());
        }

        @Test
        @DisplayName("already decided proposal → 409 proposal_not_pending")
        void notPending() {
            EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");
            p.revoke("hr-1", "wrong", NOW);
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(p));

            assertConflict(() -> service.acceptAccountLink(new AcceptAccountLinkCommand(OWNER, "p-1")),
                    "proposal_not_pending");
        }

        @Test
        @DisplayName("AC-5: employee retired after the proposal → 422 employee_not_active")
        void employeeRetiredMeanwhile() {
            Employee e = activeEmployee();
            e.retire(NOW);
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(pending("acct-A", "hr-1")));
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(e));

            assertThatThrownBy(() -> service.acceptAccountLink(new AcceptAccountLinkCommand(OWNER, "p-1")))
                    .isInstanceOf(EmployeeLinkInvalidException.class);
            assertThat(e.getAccountId()).isNull();
        }

        @Test
        @DisplayName("AC-4: account linked to another employee in the meantime → 409 account_already_linked")
        void accountLinkedMeanwhile() {
            Employee other = Employee.create("emp-2", TENANT, "E-2", "Kim", "dept-1", "cc-1", "jg-1",
                    EffectivePeriod.openEnded(LocalDate.of(2026, 1, 1)), NOW);
            other.linkAccount("acct-A", NOW);
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(pending("acct-A", "hr-1")));
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.of(other));

            assertConflict(() -> service.acceptAccountLink(new AcceptAccountLinkCommand(OWNER, "p-1")),
                    "account_already_linked");
        }

        @Test
        @DisplayName("Edge Case: the race lost at flush (DB unique) → 409 account_already_linked, proposal not saved")
        void raceLostAtFlush() {
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(pending("acct-A", "hr-1")));
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.empty());
            when(employeeRepository.saveAccountLink(any(Employee.class)))
                    .thenThrow(new EmployeeLinkConflictException("account_already_linked", "race"));

            assertConflict(() -> service.acceptAccountLink(new AcceptAccountLinkCommand(OWNER, "p-1")),
                    "account_already_linked");
            verify(linkProposalRepository, never()).save(any());
            verify(eventPublisher, never()).publishEmployeeChanged(any(), any(), any(), any(), any(), any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("decline / revoke / unlink")
    class DeclineRevokeUnlink {

        @Test
        @DisplayName("AC-7 decline by the owner → DECLINED, one audit row, no employee event")
        void decline() {
            EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(p));

            EmployeeAccountLinkProposalView v = service.declineAccountLink(
                    new DeclineAccountLinkCommand(OWNER, "p-1", "not me"));

            assertThat(v.status()).isEqualTo("DECLINED");
            assertThat(v.decisionReason()).isEqualTo("not me");
            AuditLog row = onlyAuditRow();
            assertThat(row.getAction()).isEqualTo("DECLINE_ACCOUNT_LINK");
            assertThat(row.getBeforeState()).contains("\"status\":\"PENDING\"");
            assertThat(row.getAfterState()).contains("\"status\":\"DECLINED\"");
            verify(eventPublisher, never()).publishEmployeeChanged(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("AC-3 decline by a non-owner → 403 NOT_ADDRESSEE")
        void declineByNonOwner() {
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(pending("acct-A", "hr-1")));

            assertThatThrownBy(() -> service.declineAccountLink(new DeclineAccountLinkCommand(HR, "p-1", null)))
                    .isInstanceOf(EmployeeLinkNotAddresseeException.class);
            verify(auditLogRepository, never()).append(any());
        }

        @Test
        @DisplayName("AC-7 revoke (erp.write + scope, not the proposer) → REVOKED, one audit row, no employee event")
        void revoke() {
            ActorContext otherHr = new ActorContext("hr-2", TENANT, Set.of("erp.write"), Set.of("dept-1"));
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(pending("acct-A", "hr-1")));
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));

            EmployeeAccountLinkProposalView v = service.revokeAccountLink(
                    new RevokeAccountLinkCommand(otherHr, "p-1", "wrong person"));

            assertThat(v.status()).isEqualTo("REVOKED");
            assertThat(v.decidedBy()).isEqualTo("hr-2");
            verify(authorizationPort).evaluate(otherHr, RequiredScope.WRITE, "dept-1");
            assertThat(onlyAuditRow().getAction()).isEqualTo("REVOKE_ACCOUNT_LINK");
            verify(eventPublisher, never()).publishEmployeeChanged(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("revoke outside the employee's data scope → 403 DATA_SCOPE_FORBIDDEN, no transition")
        void revokeOutOfScope() {
            EmployeeAccountLinkProposal p = pending("acct-A", "hr-1");
            when(linkProposalRepository.findById("p-1", TENANT)).thenReturn(Optional.of(p));
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));
            when(authorizationPort.evaluate(HR, RequiredScope.WRITE, "dept-1"))
                    .thenReturn(AuthorizationDecision.denyScope("out"));

            assertThatThrownBy(() -> service.revokeAccountLink(new RevokeAccountLinkCommand(HR, "p-1", "x")))
                    .isInstanceOf(DataScopeForbiddenException.class);
            assertThat(p.getStatus()).isEqualTo(LinkProposalStatus.PENDING);
        }

        @Test
        @DisplayName("AC-7 unlink by HR (scope) → accountId cleared, one audit row, one employee.changed UPDATED")
        void unlinkByHr() {
            Employee e = activeEmployee();
            e.linkAccount("acct-A", NOW);
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(e));

            EmployeeView v = service.unlinkAccount(new UnlinkAccountCommand(HR, "emp-1", "left the role"));

            assertThat(v.accountId()).isNull();
            verify(authorizationPort).evaluate(HR, RequiredScope.WRITE, "dept-1");
            AuditLog row = onlyAuditRow();
            assertThat(row.getAction()).isEqualTo("UNLINK_ACCOUNT");
            assertThat(row.getReason()).isEqualTo("left the role");
            verify(eventPublisher).publishEmployeeChanged(eq(e), eq(ChangeKind.UPDATED), eq("hr-1"),
                    any(), any(), eq("left the role"));
        }

        @Test
        @DisplayName("unlink by the linked account's owner needs no write scope")
        void unlinkByOwner() {
            Employee e = activeEmployee();
            e.linkAccount("acct-A", NOW);
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(e));

            service.unlinkAccount(new UnlinkAccountCommand(OWNER, "emp-1", "not my record"));

            assertThat(e.getAccountId()).isNull();
            verify(authorizationPort).evaluate(OWNER, RequiredScope.READ, null);
            verify(authorizationPort, never()).evaluate(OWNER, RequiredScope.WRITE, "dept-1");
        }

        @Test
        @DisplayName("unlink when not linked → 409 not_linked, no event")
        void unlinkNotLinked() {
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));

            assertConflict(() -> service.unlinkAccount(new UnlinkAccountCommand(HR, "emp-1", "x")), "not_linked");
            verify(eventPublisher, never()).publishEmployeeChanged(any(), any(), any(), any(), any(), any());
        }
    }

    // =====================================================================
    @Nested
    @DisplayName("reads: /me · /approver-ref · /mine · history")
    class Reads {

        @Test
        @DisplayName("AC-1 /me → the employee linked to the caller's sub; no department scope")
        void me() {
            Employee e = activeEmployee();
            e.linkAccount("acct-A", NOW);
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.of(e));

            EmployeeView v = service.getMyEmployee(OWNER);

            assertThat(v.id()).isEqualTo("emp-1");
            assertThat(v.accountId()).isEqualTo("acct-A");
            verify(authorizationPort).evaluate(OWNER, RequiredScope.READ, null);
        }

        @Test
        @DisplayName("AC-1 /me with no link → 404 MASTERDATA_NOT_FOUND")
        void meNotLinked() {
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMyEmployee(OWNER)).isInstanceOf(MasterdataNotFoundException.class);
        }

        @Test
        @DisplayName("/me returns a RETIRED employee too (judgement is the caller's)")
        void meRetired() {
            Employee e = activeEmployee();
            e.linkAccount("acct-A", NOW);
            e.retire(NOW);
            when(employeeRepository.findByAccountId("acct-A", TENANT)).thenReturn(Optional.of(e));

            assertThat(service.getMyEmployee(OWNER).status()).isEqualTo("RETIRED");
        }

        @Test
        @DisplayName("/approver-ref → {id,status,accountId?}; no department scope; 404 when absent")
        void approverRef() {
            Employee e = activeEmployee();
            e.linkAccount("acct-A", NOW);
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(e));
            when(employeeRepository.findById("emp-x", TENANT)).thenReturn(Optional.empty());

            EmployeeApproverRefView v = service.getApproverRef("emp-1", HR);

            assertThat(v).isEqualTo(new EmployeeApproverRefView("emp-1", "ACTIVE", "acct-A"));
            verify(authorizationPort, never()).evaluate(any(), any(), eq("dept-1"));
            assertThatThrownBy(() -> service.getApproverRef("emp-x", HR))
                    .isInstanceOf(MasterdataNotFoundException.class);
        }

        @Test
        @DisplayName("/mine → PENDING proposals addressed to the caller, with employee name + number")
        void mine() {
            when(linkProposalRepository.findPendingByAccountId("acct-A", TENANT, 0, 20))
                    .thenReturn(new PageResult<>(List.of(pending("acct-A", "hr-1")), 0, 20, 1L, 1));
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));

            PageResult<EmployeeAccountLinkProposalView> r = service.listMyPendingLinkProposals(OWNER, 0, 20);

            assertThat(r.content()).singleElement().satisfies(v -> {
                assertThat(v.employeeName()).isEqualTo("Hong");
                assertThat(v.employeeNumber()).isEqualTo("E-1");
                assertThat(v.status()).isEqualTo("PENDING");
            });
            verify(authorizationPort).evaluate(OWNER, RequiredScope.READ, null);
        }

        @Test
        @DisplayName("history → erp.read + the employee's department scope")
        void history() {
            when(employeeRepository.findById("emp-1", TENANT)).thenReturn(Optional.of(activeEmployee()));
            when(linkProposalRepository.findByEmployeeId("emp-1", TENANT, 0, 20))
                    .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

            service.listEmployeeLinkProposals("emp-1", HR, 0, 20);

            verify(authorizationPort).evaluate(HR, RequiredScope.READ, "dept-1");
        }
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String cause) {
        assertThatThrownBy(call).isInstanceOfSatisfying(EmployeeLinkConflictException.class, ex -> {
            assertThat(ex.conflictCause()).isEqualTo(cause);
            assertThat(ex.code()).isEqualTo("EMPLOYEE_LINK_CONFLICT");
            assertThat(ex.details()).containsEntry("cause", cause);
        });
    }
}
