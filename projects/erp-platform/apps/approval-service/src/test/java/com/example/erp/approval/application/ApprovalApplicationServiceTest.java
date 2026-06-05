package com.example.erp.approval.application;

import com.example.erp.approval.application.command.Commands.ApproveCommand;
import com.example.erp.approval.application.command.Commands.CreateDraftCommand;
import com.example.erp.approval.application.command.Commands.RejectCommand;
import com.example.erp.approval.application.command.Commands.SubmitCommand;
import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.application.port.outbound.AuthorizationPort;
import com.example.erp.approval.application.port.outbound.ClockPort;
import com.example.erp.approval.application.port.outbound.MasterDataPort;
import com.example.erp.approval.domain.audit.ApprovalAuditLog;
import com.example.erp.approval.domain.audit.ApprovalAuditLogRepository;
import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.delegation.DelegationGrantRepository;
import com.example.erp.approval.domain.delegation.DelegationResolver;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalNotAuthorizedApproverException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRequestNotFoundException;
import com.example.erp.approval.domain.error.ApprovalErrors.ApprovalRouteInvalidException;
import com.example.erp.approval.domain.error.ApprovalErrors.PermissionDeniedException;
import com.example.erp.approval.domain.request.ApprovalAction;
import com.example.erp.approval.domain.request.ApprovalRequest;
import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.erp.approval.domain.request.ApprovalSubject;
import com.example.erp.approval.domain.request.SubjectType;
import com.example.erp.approval.domain.request.repository.ApprovalRequestRepository;
import com.example.erp.approval.domain.route.ApprovalRoute;
import com.example.erp.approval.domain.route.ApprovalRouteStage;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application unit tests for {@link ApprovalApplicationService} — proves the
 * single-Tx contract: authorization is checked BEFORE any repository call (E6);
 * the masterdata ref-check gates submit (E1); a transition writes an action +
 * audit row + outbox event; approver-eligibility (I4) and finalized guards
 * reject. {@code STRICT_STUBS}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApprovalApplicationServiceTest {

    private static final String TENANT = "erp";
    private static final Instant NOW = Instant.parse("2026-06-05T00:00:00Z");
    private static final ActorContext SUBMITTER = new ActorContext("emp-sub", TENANT,
            Set.of("erp.write", "erp.read"), Set.of("*"));
    private static final ActorContext APPROVER = new ActorContext("emp-app", TENANT,
            Set.of("erp.write", "erp.read"), Set.of("*"));
    private static final ActorContext NO_ROLE = new ActorContext("emp-x", TENANT,
            Set.of(), Set.of());

    @Mock ApprovalRequestRepository requestRepository;
    @Mock ApprovalAuditLogRepository auditLogRepository;
    @Mock ApprovalEventPublisher eventPublisher;
    @Mock MasterDataPort masterDataPort;
    @Mock AuthorizationPort authorizationPort;
    @Mock DelegationGrantRepository delegationGrantRepository;
    @Mock ClockPort clock;

    ApprovalApplicationService service;

    @BeforeEach
    void stubDefaults() {
        DelegationResolver delegationResolver = new DelegationResolver(delegationGrantRepository);
        service = new ApprovalApplicationService(requestRepository, auditLogRepository,
                eventPublisher, masterDataPort, authorizationPort, delegationResolver,
                clock, new ObjectMapper());
        lenient().when(clock.now()).thenReturn(NOW);
        lenient().when(requestRepository.save(any(ApprovalRequest.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(requestRepository.saveAndFlush(any(ApprovalRequest.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(requestRepository.appendAction(any(ApprovalAction.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(auditLogRepository.append(any(ApprovalAuditLog.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(requestRepository.findActions(any(), any()))
                .thenReturn(List.of());
        lenient().when(requestRepository.findStages(any(), any()))
                .thenReturn(List.of());
        lenient().when(requestRepository.saveStages(any()))
                .thenAnswer(i -> i.getArgument(0));
    }

    private ApprovalRequest draftFor(String submitter, String approver) {
        return ApprovalRequest.createDraft("appr-1", TENANT,
                new ApprovalSubject(SubjectType.DEPARTMENT, "dept-1"), "title", null,
                ApprovalRoute.singleStage(submitter, approver), submitter, NOW);
    }

    private ApprovalRequest draftFor(String submitter, List<String> approvers) {
        return ApprovalRequest.createDraft("appr-1", TENANT,
                new ApprovalSubject(SubjectType.DEPARTMENT, "dept-1"), "title", null,
                ApprovalRoute.multiStage(submitter, approvers), submitter, NOW);
    }

    private void stubRoute(String submitter, List<String> approvers) {
        when(requestRepository.loadRoute("appr-1", TENANT))
                .thenReturn(ApprovalRoute.multiStage(submitter, approvers));
    }

    // ---- create ----

    @Test
    @DisplayName("create → DRAFT persisted, stages saved, no event")
    void create() {
        var view = service.createDraft(new CreateDraftCommand(
                SUBMITTER, SubjectType.DEPARTMENT, "dept-1", "title", "r", "emp-app"));
        assertThat(view.status()).isEqualTo("DRAFT");
        verify(requestRepository).save(any(ApprovalRequest.class));
        verify(requestRepository).saveStages(any());
        verify(eventPublisher, never()).publishSubmitted(any(), any());
    }

    @Test
    @DisplayName("create multi-stage → totalStages reflects the route, stage rows saved")
    void createMultiStage() {
        var view = service.createDraft(new CreateDraftCommand(
                SUBMITTER, SubjectType.DEPARTMENT, "dept-1", "title", null,
                List.of("emp-app1", "emp-app2")));
        assertThat(view.status()).isEqualTo("DRAFT");
        assertThat(view.totalStages()).isEqualTo(2);
        verify(requestRepository).saveStages(any());
    }

    @Test
    @DisplayName("create with duplicate stage approver → APPROVAL_ROUTE_INVALID")
    void createDuplicateStageApprover() {
        assertThatThrownBy(() -> service.createDraft(new CreateDraftCommand(
                SUBMITTER, SubjectType.DEPARTMENT, "dept-1", "title", null,
                List.of("emp-a", "emp-a"))))
                .isInstanceOf(ApprovalRouteInvalidException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("create with self-approval (submitter == approver) → APPROVAL_ROUTE_INVALID")
    void createSelfApproval() {
        assertThatThrownBy(() -> service.createDraft(new CreateDraftCommand(
                SUBMITTER, SubjectType.DEPARTMENT, "dept-1", "title", null, "emp-sub")))
                .isInstanceOf(ApprovalRouteInvalidException.class);
        verify(requestRepository, never()).save(any());
    }

    @Test
    @DisplayName("E6: no write role → PermissionDenied (no repository call)")
    void createNoRole() {
        when(authorizationPort.evaluate(any(), any(), any()))
                .thenReturn(com.example.erp.approval.domain.authorization.AuthorizationDecision
                        .denyRole("nope"));
        assertThatThrownBy(() -> service.createDraft(new CreateDraftCommand(
                NO_ROLE, SubjectType.DEPARTMENT, "dept-1", "title", null, "emp-app")))
                .isInstanceOf(PermissionDeniedException.class);
        verify(requestRepository, never()).save(any());
    }

    // ---- submit + E1 ref-check ----

    @Test
    @DisplayName("submit: subject ACTIVE → SUBMITTED + event + audit")
    void submitActive() {
        ApprovalRequest draft = draftFor("emp-sub", "emp-app");
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(draft));
        when(masterDataPort.isSubjectActive(any(ApprovalSubject.class), eqTenant()))
                .thenReturn(true);

        var view = service.submit(new SubmitCommand(SUBMITTER, "appr-1"));

        assertThat(view.status()).isEqualTo("SUBMITTED");
        verify(requestRepository).appendAction(any(ApprovalAction.class));
        verify(auditLogRepository).append(any(ApprovalAuditLog.class));
        verify(eventPublisher).publishSubmitted(any(ApprovalRequest.class), any());
    }

    @Test
    @DisplayName("submit: subject not ACTIVE (E1) → APPROVAL_ROUTE_INVALID, stays DRAFT, no event")
    void submitRetiredSubject() {
        ApprovalRequest draft = draftFor("emp-sub", "emp-app");
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(draft));
        when(masterDataPort.isSubjectActive(any(ApprovalSubject.class), eqTenant()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.submit(new SubmitCommand(SUBMITTER, "appr-1")))
                .isInstanceOf(ApprovalRouteInvalidException.class);

        assertThat(draft.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
        verify(requestRepository, never()).saveAndFlush(any());
        verify(eventPublisher, never()).publishSubmitted(any(), any());
    }

    @Test
    @DisplayName("submit: unknown id → APPROVAL_REQUEST_NOT_FOUND")
    void submitNotFound() {
        when(requestRepository.findById("missing", TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.submit(new SubmitCommand(SUBMITTER, "missing")))
                .isInstanceOf(ApprovalRequestNotFoundException.class);
    }

    // ---- approve authz (I4) ----

    @Test
    @DisplayName("single-stage approve by the route approver → APPROVED + event")
    void approveByApprover() {
        ApprovalRequest submitted = draftFor("emp-sub", "emp-app");
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));
        stubRoute("emp-sub", List.of("emp-app"));

        var view = service.approve(new ApproveCommand(APPROVER, "appr-1", null));

        assertThat(view.status()).isEqualTo("APPROVED");
        verify(eventPublisher).publishApproved(any(ApprovalRequest.class), any(), any(), any());
    }

    @Test
    @DisplayName("approve by a non-approver → APPROVAL_NOT_AUTHORIZED_APPROVER, no event")
    void approveByNonApprover() {
        ApprovalRequest submitted = draftFor("emp-sub", "emp-app");
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));
        stubRoute("emp-sub", List.of("emp-app"));

        ActorContext wrong = new ActorContext("emp-other", TENANT,
                Set.of("erp.write"), Set.of("*"));
        assertThatThrownBy(() -> service.approve(new ApproveCommand(wrong, "appr-1", null)))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
        verify(eventPublisher, never()).publishApproved(any(), any(), any(), any());
    }

    // ---- multi-stage event-emission points (TASK-ERP-BE-012) ----

    @Test
    @DisplayName("2-stage: intermediate approve → IN_REVIEW, audit row but NO approved event")
    void intermediateApproveEmitsNoEvent() {
        ApprovalRequest submitted = draftFor("emp-sub", List.of("emp-app1", "emp-app2"));
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));
        stubRoute("emp-sub", List.of("emp-app1", "emp-app2"));

        ActorContext stage0 = new ActorContext("emp-app1", TENANT, Set.of("erp.write"), Set.of("*"));
        var view = service.approve(new ApproveCommand(stage0, "appr-1", null));

        assertThat(view.status()).isEqualTo("IN_REVIEW");
        // audit + action row written, but no outbox event on the intermediate stage.
        verify(requestRepository).appendAction(any(ApprovalAction.class));
        verify(auditLogRepository).append(any(ApprovalAuditLog.class));
        verify(eventPublisher, never()).publishApproved(any(), any(), any(), any());
    }

    @Test
    @DisplayName("2-stage: final approve → APPROVED, approved event emitted once")
    void finalApproveEmitsEvent() {
        ApprovalRequest submitted = draftFor("emp-sub", List.of("emp-app1", "emp-app2"));
        submitted.submit(NOW);
        // advance to IN_REVIEW at stage 1 first
        submitted.approve("emp-app1", ApprovalRoute.multiStage("emp-sub",
                List.of("emp-app1", "emp-app2")), NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));
        stubRoute("emp-sub", List.of("emp-app1", "emp-app2"));

        ActorContext stage1 = new ActorContext("emp-app2", TENANT, Set.of("erp.write"), Set.of("*"));
        var view = service.approve(new ApproveCommand(stage1, "appr-1", null));

        assertThat(view.status()).isEqualTo("APPROVED");
        verify(eventPublisher).publishApproved(any(ApprovalRequest.class), any(), any(), any());
    }

    // ---- delegation (대결) approve resolution (TASK-ERP-BE-013) ----

    private DelegationGrant activeGrant(String delegator, String delegate) {
        return DelegationGrant.create("dgr-1", TENANT, delegator, delegate,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-30T00:00:00Z"), null, delegator,
                Instant.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    @DisplayName("delegated approve: active grant A→D, D approves → APPROVED, onBehalfOf=A in event")
    void delegatedApprove() {
        ApprovalRequest submitted = draftFor("emp-sub", "emp-app");
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));
        stubRoute("emp-sub", List.of("emp-app"));
        when(delegationGrantRepository.findActiveGrant("emp-app", "emp-d", TENANT, NOW))
                .thenReturn(Optional.of(activeGrant("emp-app", "emp-d")));

        ActorContext delegate = new ActorContext("emp-d", TENANT, Set.of("erp.write"), Set.of("*"));
        var view = service.approve(new ApproveCommand(delegate, "appr-1", null));

        assertThat(view.status()).isEqualTo("APPROVED");
        // actingForApproverId = the stage approver A (= emp-app) is carried to the event.
        verify(eventPublisher).publishApproved(any(ApprovalRequest.class), org.mockito.ArgumentMatchers.eq("emp-d"),
                any(), org.mockito.ArgumentMatchers.eq("emp-app"));
        // the audit/action row records actor=D + onBehalfOf=A.
        org.mockito.ArgumentCaptor<ApprovalAction> actionCaptor =
                org.mockito.ArgumentCaptor.forClass(ApprovalAction.class);
        verify(requestRepository).appendAction(actionCaptor.capture());
        assertThat(actionCaptor.getValue().getActor()).isEqualTo("emp-d");
        assertThat(actionCaptor.getValue().getOnBehalfOf()).isEqualTo("emp-app");
    }

    @Test
    @DisplayName("no active grant for the other principal → APPROVAL_NOT_AUTHORIZED_APPROVER")
    void noGrantOtherPrincipal() {
        ApprovalRequest submitted = draftFor("emp-sub", "emp-app");
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));
        stubRoute("emp-sub", List.of("emp-app"));
        when(delegationGrantRepository.findActiveGrant("emp-app", "emp-other", TENANT, NOW))
                .thenReturn(Optional.empty());

        ActorContext other = new ActorContext("emp-other", TENANT, Set.of("erp.write"), Set.of("*"));
        assertThatThrownBy(() -> service.approve(new ApproveCommand(other, "appr-1", null)))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
        verify(eventPublisher, never()).publishApproved(any(), any(), any(), any());
    }

    @Test
    @DisplayName("delegate == submitter → refused (self-approval-via-delegation, SoD)")
    void delegateIsSubmitterRefused() {
        // route: submitter=emp-sub, approver=emp-app. A grant emp-app→emp-sub would let
        // the submitter approve via delegation — must be refused.
        ApprovalRequest submitted = draftFor("emp-sub", "emp-app");
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));
        stubRoute("emp-sub", List.of("emp-app"));
        when(delegationGrantRepository.findActiveGrant("emp-app", "emp-sub", TENANT, NOW))
                .thenReturn(Optional.of(activeGrant("emp-app", "emp-sub")));

        ActorContext submitterAsDelegate = new ActorContext("emp-sub", TENANT,
                Set.of("erp.write"), Set.of("*"));
        assertThatThrownBy(() -> service.approve(new ApproveCommand(submitterAsDelegate, "appr-1", null)))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
        verify(eventPublisher, never()).publishApproved(any(), any(), any(), any());
    }

    @Test
    @DisplayName("expired grant → not authorized (isActiveAt re-check fails)")
    void expiredGrantNotAuthorized() {
        ApprovalRequest submitted = draftFor("emp-sub", "emp-app");
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));
        stubRoute("emp-sub", List.of("emp-app"));
        DelegationGrant expired = DelegationGrant.create("dgr-2", TENANT, "emp-app", "emp-d",
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-31T00:00:00Z"), null, "emp-app",
                Instant.parse("2026-05-01T00:00:00Z"));
        when(delegationGrantRepository.findActiveGrant("emp-app", "emp-d", TENANT, NOW))
                .thenReturn(Optional.of(expired));

        ActorContext delegate = new ActorContext("emp-d", TENANT, Set.of("erp.write"), Set.of("*"));
        assertThatThrownBy(() -> service.approve(new ApproveCommand(delegate, "appr-1", null)))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
    }

    @Test
    @DisplayName("delegated reject: active grant A→D, D rejects (reason) → REJECTED, onBehalfOf=A in event")
    void delegatedReject() {
        ApprovalRequest submitted = draftFor("emp-sub", "emp-app");
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));
        stubRoute("emp-sub", List.of("emp-app"));
        when(delegationGrantRepository.findActiveGrant("emp-app", "emp-d", TENANT, NOW))
                .thenReturn(Optional.of(activeGrant("emp-app", "emp-d")));

        ActorContext delegate = new ActorContext("emp-d", TENANT, Set.of("erp.write"), Set.of("*"));
        var view = service.reject(new RejectCommand(delegate, "appr-1", "not ok"));

        assertThat(view.status()).isEqualTo("REJECTED");
        verify(eventPublisher).publishRejected(any(ApprovalRequest.class),
                org.mockito.ArgumentMatchers.eq("emp-d"), any(),
                org.mockito.ArgumentMatchers.eq("emp-app"));
    }

    private static String eqTenant() {
        return org.mockito.ArgumentMatchers.eq(TENANT);
    }
}
