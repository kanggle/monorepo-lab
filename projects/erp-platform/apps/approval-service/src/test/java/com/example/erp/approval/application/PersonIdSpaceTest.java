package com.example.erp.approval.application;

import com.example.erp.approval.application.command.Commands.ApproveCommand;
import com.example.erp.approval.application.command.Commands.CreateDraftCommand;
import com.example.erp.approval.application.command.Commands.SubmitCommand;
import com.example.erp.approval.application.command.Commands.WithdrawCommand;
import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.application.port.outbound.AuthorizationPort;
import com.example.erp.approval.application.port.outbound.ClockPort;
import com.example.erp.approval.application.port.outbound.EmployeeLookup;
import com.example.erp.approval.application.port.outbound.MasterDataPort;
import com.example.erp.approval.domain.audit.ApprovalAuditLog;
import com.example.erp.approval.domain.audit.ApprovalAuditLogRepository;
import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.delegation.DelegationGrantRepository;
import com.example.erp.approval.domain.delegation.DelegationResolver;
import com.example.erp.approval.domain.delegation.DelegationScope;
import com.example.erp.approval.domain.error.ApprovalDomainException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalActorNotLinkedException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalApproverUnlinkedException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalNotAuthorizedApproverException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRouteInvalidException;
import com.example.erp.approval.domain.error.ApprovalErrors.PersonResolveUnavailableException;
import com.example.erp.approval.domain.request.ApprovalAction;
import com.example.erp.approval.domain.request.ApprovalSubject;
import com.example.erp.approval.domain.request.SubjectType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * TASK-MONO-776 «후» — every person field is an EMPLOYEE id and the caller side is «the
 * employee linked to my sub» (approval-api.md § v2.4). Accounts ({@code acc-*}) and employees
 * ({@code emp-*}) are deliberately DISTINCT ids — the pre-776 tests used one string for both
 * and therefore could not see the defect.
 *
 * <p>«전» record (same scenarios, run on the code before this change, 2026-10-07 UTC): the
 * inbox of the account linked to the approver employee was <b>0</b> and creating a request
 * with «my own linked employee» as approver <b>passed</b> (DRAFT, submitterId = the sub) —
 * both green on the old code, both red on the new code (inbox 1; self_approval thrown).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PersonIdSpaceTest {

    private static final String TENANT = "demo-corp";
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    @Mock ApprovalAuditLogRepository auditLogRepository;
    @Mock ApprovalEventPublisher eventPublisher;
    @Mock MasterDataPort masterDataPort;
    @Mock AuthorizationPort authorizationPort;
    @Mock DelegationGrantRepository delegationGrantRepository;
    @Mock ClockPort clock;

    /** account sub → linked employee (absent = unlinked). */
    private final Map<String, String> links = new HashMap<>();
    /** employee id → lookup (absent = no such employee). */
    private final Map<String, EmployeeLookup> employees = new HashMap<>();

    private InMemoryApprovalRequestRepository repo;
    private ApprovalApplicationService service;

    @BeforeEach
    void world() {
        employee("emp-sub", "ACTIVE", "acc-sub");
        employee("emp-app", "ACTIVE", "acc-app");
        employee("emp-app2", "ACTIVE", "acc-app2");
        employee("emp-dlg", "ACTIVE", "acc-dlg");
        employee("emp-retired", "RETIRED", "acc-retired");
        employee("emp-nolink", "ACTIVE", null);

        lenient().when(clock.now()).thenReturn(NOW);
        lenient().when(masterDataPort.isSubjectActive(any(ApprovalSubject.class), eq(TENANT)))
                .thenReturn(true);
        lenient().when(masterDataPort.callerEmployee(anyString(), eq(TENANT))).thenAnswer(i -> {
            String emp = links.get((String) i.getArgument(0));
            return emp == null ? EmployeeLookup.notFound() : employees.get(emp);
        });
        lenient().when(masterDataPort.approverRef(anyString(), eq(TENANT))).thenAnswer(i ->
                employees.getOrDefault((String) i.getArgument(0), EmployeeLookup.notFound()));
        lenient().when(auditLogRepository.append(any(ApprovalAuditLog.class)))
                .thenAnswer(i -> i.getArgument(0));

        repo = new InMemoryApprovalRequestRepository();
        service = new ApprovalApplicationService(repo, auditLogRepository, eventPublisher,
                masterDataPort, authorizationPort, new DelegationResolver(delegationGrantRepository),
                clock, new ObjectMapper());
    }

    private void employee(String id, String status, String accountId) {
        employees.put(id, EmployeeLookup.found(id, status, accountId));
        if (accountId != null) {
            links.put(accountId, id);
        }
    }

    private static ActorContext account(String sub) {
        return new ActorContext(sub, TENANT, Set.of("erp.write", "erp.read"), Set.of("*"));
    }

    private String draft(String sub, String... approvers) {
        return service.createDraft(new CreateDraftCommand(account(sub), SubjectType.DEPARTMENT,
                "dept-1", "t", null, List.of(approvers))).id();
    }

    private static String code(Throwable t) {
        return ((ApprovalDomainException) t).code();
    }

    // ---- AC-1 ----

    @Test
    @DisplayName("AC-1: approver = employee id → in the LINKED account's inbox; meta id = that employee")
    void inboxOfTheLinkedAccount() {
        String id = draft("acc-sub", "emp-app");
        service.submit(new SubmitCommand(account("acc-sub"), id));

        var mine = service.inbox(account("acc-app"), 0, 20);
        assertThat(mine.page().totalElements()).isEqualTo(1);
        assertThat(mine.actorEmployeeId()).isEqualTo("emp-app");
        assertThat(service.inbox(account("acc-app2"), 0, 20).page().totalElements()).isZero();
    }

    @Test
    @DisplayName("history actor = employee, audit actor = sub; submitterId = employee")
    void historyEmployeeAuditSub() {
        String id = draft("acc-sub", "emp-app");
        var view = service.submit(new SubmitCommand(account("acc-sub"), id));

        assertThat(view.submitterId()).isEqualTo("emp-sub");
        assertThat(repo.actions()).extracting(ApprovalAction::getActor).containsExactly("emp-sub");
        ArgumentCaptor<ApprovalAuditLog> audit = ArgumentCaptor.forClass(ApprovalAuditLog.class);
        verify(auditLogRepository).append(audit.capture());
        assertThat(audit.getValue().getActor()).isEqualTo("acc-sub");
        verify(eventPublisher).publishSubmitted(any(), eq("emp-sub"));
    }

    // ---- AC-2 ----

    @Test
    @DisplayName("AC-2: missing / RETIRED / unavailable approver → 422 approver_unresolved, stays DRAFT")
    void approverUnresolved() {
        employees.put("emp-down", EmployeeLookup.unavailable());
        for (String approver : List.of("emp-ghost", "emp-retired", "emp-down")) {
            String id = draft("acc-sub", approver);
            assertThatThrownBy(() -> service.submit(new SubmitCommand(account("acc-sub"), id)))
                    .isInstanceOf(ApprovalRouteInvalidException.class)
                    .satisfies(e -> assertThat(((ApprovalDomainException) e).details())
                            .isEqualTo(Map.of("cause", "approver_unresolved")));
            assertThat(repo.findById(id, TENANT).orElseThrow().getStatus().name()).isEqualTo("DRAFT");
        }
    }

    // ---- AC-3 ----

    @Test
    @DisplayName("AC-3: approver = the employee linked to MY account → 422 self_approval")
    void selfApprovalThroughTheLink() {
        assertThatThrownBy(() -> draft("acc-sub", "emp-sub"))
                .isInstanceOf(ApprovalRouteInvalidException.class)
                .satisfies(e -> assertThat(((ApprovalDomainException) e).details())
                        .isEqualTo(Map.of("cause", "self_approval")));
    }

    // ---- AC-4 ----

    @Test
    @DisplayName("AC-4: ACTIVE but unlinked stage approver → 422 APPROVAL_APPROVER_UNLINKED (stageIndex)")
    void unlinkedApprover() {
        String id = draft("acc-sub", "emp-app", "emp-nolink");
        assertThatThrownBy(() -> service.submit(new SubmitCommand(account("acc-sub"), id)))
                .isInstanceOf(ApprovalApproverUnlinkedException.class)
                .satisfies(e -> assertThat(((ApprovalDomainException) e).details())
                        .isEqualTo(Map.of("stageIndex", 1)));
    }

    @Test
    @DisplayName("AC-4: unlinked / RETIRED-linked caller → 403 APPROVAL_ACTOR_NOT_LINKED on create")
    void unlinkedOrRetiredCaller() {
        assertThatThrownBy(() -> draft("acc-stranger", "emp-app"))
                .isInstanceOf(ApprovalActorNotLinkedException.class);
        assertThatThrownBy(() -> draft("acc-retired", "emp-app"))
                .isInstanceOf(ApprovalActorNotLinkedException.class);
    }

    @Test
    @DisplayName("AC-4: unlinked caller → inbox / ?role= empty, actorEmployeeId absent (no error)")
    void unlinkedCallerReadsEmpty() {
        String id = draft("acc-sub", "emp-app");
        service.submit(new SubmitCommand(account("acc-sub"), id));

        var inbox = service.inbox(account("acc-stranger"), 0, 20);
        assertThat(inbox.page().totalElements()).isZero();
        assertThat(inbox.actorEmployeeId()).isNull();
        assertThat(service.list(account("acc-stranger"), null,
                ApprovalApplicationService.ParticipantRole.APPROVER, 0, 20).totalElements()).isZero();
        assertThat(service.list(account("acc-app"), null,
                ApprovalApplicationService.ParticipantRole.APPROVER, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("«who am I» unavailable → 503 SERVICE_UNAVAILABLE, never «not linked»")
    void callerLookupUnavailable() {
        lenient().when(masterDataPort.callerEmployee(eq("acc-sub"), eq(TENANT)))
                .thenReturn(EmployeeLookup.unavailable());
        assertThatThrownBy(() -> draft("acc-sub", "emp-app"))
                .isInstanceOf(PersonResolveUnavailableException.class)
                .satisfies(e -> assertThat(code(e)).isEqualTo("SERVICE_UNAVAILABLE"));
        assertThatThrownBy(() -> service.inbox(account("acc-sub"), 0, 20))
                .isInstanceOf(PersonResolveUnavailableException.class);
    }

    // ---- withdraw / approve ----

    @Test
    @DisplayName("withdraw: caller's employee == submitterId; another linked account is refused")
    void withdrawBySubmitterEmployee() {
        String id = draft("acc-sub", "emp-app");
        service.submit(new SubmitCommand(account("acc-sub"), id));
        assertThatThrownBy(() -> service.withdraw(new WithdrawCommand(account("acc-app"), id, "r")))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
        assertThat(service.withdraw(new WithdrawCommand(account("acc-sub"), id, "r")).status())
                .isEqualTo("WITHDRAWN");
    }

    // ---- AC-5 ----

    @Test
    @DisplayName("AC-5: delegate's linked account approves for the approver employee (employee ids)")
    void delegatedApprove() {
        String id = draft("acc-sub", "emp-app");
        service.submit(new SubmitCommand(account("acc-sub"), id));
        lenient().when(delegationGrantRepository.findActiveGrant("emp-app", "emp-dlg", TENANT, id, NOW))
                .thenReturn(Optional.of(grant("emp-app", "emp-dlg")));

        var view = service.approve(new ApproveCommand(account("acc-dlg"), id, null));
        assertThat(view.status()).isEqualTo("APPROVED");
        verify(eventPublisher).publishApproved(any(), eq("emp-dlg"), any(), eq("emp-app"));
    }

    @Test
    @DisplayName("AC-5: delegate = the submitter's employee → SoD refusal")
    void delegateIsSubmitter() {
        String id = draft("acc-sub", "emp-app");
        service.submit(new SubmitCommand(account("acc-sub"), id));
        lenient().when(delegationGrantRepository.findActiveGrant("emp-app", "emp-sub", TENANT, id, NOW))
                .thenReturn(Optional.of(grant("emp-app", "emp-sub")));

        assertThatThrownBy(() -> service.approve(new ApproveCommand(account("acc-sub"), id, null)))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
    }

    private static DelegationGrant grant(String delegator, String delegate) {
        return DelegationGrant.create("dgr-1", TENANT, delegator, delegate,
                Instant.parse("2026-01-01T00:00:00Z"), null, null, DelegationScope.GLOBAL, null,
                "acc-x", Instant.parse("2026-01-01T00:00:00Z"));
    }
}
