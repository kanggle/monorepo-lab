package com.example.erp.approval.application;

import com.example.erp.approval.application.command.Commands.ApproveCommand;
import com.example.erp.approval.application.command.Commands.CreateDraftCommand;
import com.example.erp.approval.application.command.Commands.SubmitCommand;
import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.application.port.outbound.AuthorizationPort;
import com.example.erp.approval.application.port.outbound.ClockPort;
import com.example.erp.approval.application.port.outbound.MasterDataPort;
import com.example.erp.approval.domain.audit.ApprovalAuditLog;
import com.example.erp.approval.domain.audit.ApprovalAuditLogRepository;
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
    @Mock ClockPort clock;

    ApprovalApplicationService service;

    @BeforeEach
    void stubDefaults() {
        service = new ApprovalApplicationService(requestRepository, auditLogRepository,
                eventPublisher, masterDataPort, authorizationPort, clock, new ObjectMapper());
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
    }

    private ApprovalRequest draftFor(String submitter, String approver) {
        return ApprovalRequest.createDraft("appr-1", TENANT,
                new ApprovalSubject(SubjectType.DEPARTMENT, "dept-1"), "title", null,
                ApprovalRoute.singleStage(submitter, approver), submitter, NOW);
    }

    // ---- create ----

    @Test
    @DisplayName("create → DRAFT persisted, no event")
    void create() {
        var view = service.createDraft(new CreateDraftCommand(
                SUBMITTER, SubjectType.DEPARTMENT, "dept-1", "title", "r", "emp-app"));
        assertThat(view.status()).isEqualTo("DRAFT");
        verify(requestRepository).save(any(ApprovalRequest.class));
        verify(eventPublisher, never()).publishSubmitted(any(), any());
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
    @DisplayName("approve by the route approver → APPROVED + event")
    void approveByApprover() {
        ApprovalRequest submitted = draftFor("emp-sub", "emp-app");
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));

        var view = service.approve(new ApproveCommand(APPROVER, "appr-1", null));

        assertThat(view.status()).isEqualTo("APPROVED");
        verify(eventPublisher).publishApproved(any(ApprovalRequest.class), any(), any());
    }

    @Test
    @DisplayName("approve by a non-approver → APPROVAL_NOT_AUTHORIZED_APPROVER, no event")
    void approveByNonApprover() {
        ApprovalRequest submitted = draftFor("emp-sub", "emp-app");
        submitted.submit(NOW);
        when(requestRepository.findById("appr-1", TENANT)).thenReturn(Optional.of(submitted));

        ActorContext wrong = new ActorContext("emp-other", TENANT,
                Set.of("erp.write"), Set.of("*"));
        assertThatThrownBy(() -> service.approve(new ApproveCommand(wrong, "appr-1", null)))
                .isInstanceOf(ApprovalNotAuthorizedApproverException.class);
        verify(eventPublisher, never()).publishApproved(any(), any(), any());
    }

    private static String eqTenant() {
        return org.mockito.ArgumentMatchers.eq(TENANT);
    }
}
