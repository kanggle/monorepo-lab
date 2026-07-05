package com.example.admin.application;

import com.example.admin.application.PartnershipManagementUseCase.InvitePartnershipCommand;
import com.example.admin.application.event.PartnershipEventPublisher;
import com.example.admin.application.exception.PartnershipAlreadyExistsException;
import com.example.admin.application.exception.PartnershipNotFoundException;
import com.example.admin.application.exception.PartnershipScopeDeniedException;
import com.example.admin.application.exception.PartnershipScopeInvalidException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.PartnershipTransitionInvalidException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.TenantDomainSubscriptionPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.application.port.TenantPartnershipPort.PartnershipView;
import com.example.admin.application.tenant.TenantDomainSubscriptionSummary;
import com.example.admin.domain.rbac.PartnershipStatus;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.ScopeSet;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-477 / ADR-MONO-045 — unit tests for {@link PartnershipManagementUseCase}:
 * the D2 two-sided consent gate, the {@code delegatedScope} cap, the state machine,
 * and the one-shot terminate event.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PartnershipManagementUseCaseTest {

    private static final String HOST = "acme-corp";
    private static final String PARTNER = "globex";
    private static final String PID = "00000000-0000-7000-8000-00000000p001";

    @Mock TenantPartnershipPort partnershipPort;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock AdminActionAuditor auditor;
    @Mock PartnershipEventPublisher eventPublisher;
    @Mock AdminOperatorPort operatorPort;
    @Mock TenantDomainSubscriptionPort subscriptionPort;

    private PartnershipManagementUseCase useCase() {
        return new PartnershipManagementUseCase(partnershipPort, tenantScopeGuard, auditor,
                eventPublisher, operatorPort, subscriptionPort);
    }

    /** Stub the host's ACTIVE domain subscriptions (TASK-BE-479 invite-time ≤-own). */
    private void stubHostSubscribes(String... domains) {
        List<TenantDomainSubscriptionSummary> subs = new java.util.ArrayList<>();
        for (String d : domains) {
            subs.add(new TenantDomainSubscriptionSummary(HOST, d));
        }
        // include an unrelated tenant's sub to prove the host-filter works.
        subs.add(new TenantDomainSubscriptionSummary("other-tenant", "finance"));
        when(subscriptionPort.listActiveSubscriptions()).thenReturn(subs);
    }

    private OperatorContext actor() {
        return new OperatorContext("op-host", "jti-1");
    }

    private PartnershipView view(PartnershipStatus status) {
        return new PartnershipView(1L, PID, HOST, PARTNER, status,
                ScopeSet.of(List.of("wms", "scm"), List.of("WMS_OP", "SCM_PLANNER")),
                null, null, Instant.parse("2026-07-04T10:00:00Z"), null, null);
    }

    // ── invite ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("invite: PENDING created, host-side D2 guard, event + audit")
    void invite_happyPath() {
        when(partnershipPort.pairExists(HOST, PARTNER)).thenReturn(false);
        when(partnershipPort.createPending(any())).thenReturn(view(PartnershipStatus.PENDING));
        stubHostSubscribes("wms", "scm"); // host holds both delegated domains

        PartnershipView result = useCase().invite(HOST,
                new InvitePartnershipCommand(PARTNER, List.of("wms", "scm"),
                        List.of("WMS_OPERATOR", "SCM_OPERATOR")),
                actor(), "collab");

        assertThat(result.status()).isEqualTo(PartnershipStatus.PENDING);
        verify(tenantScopeGuard).requireTenantInScope(
                any(), eq(Permission.PARTNERSHIP_MANAGE), eq(HOST), eq(ActionCode.PARTNERSHIP_INVITE));
        verify(eventPublisher).publishInvited(eq(PID), eq(HOST), eq(PARTNER), any(), any(), any());
        verify(auditor).recordWithPermission(any(), eq(Permission.PARTNERSHIP_MANAGE));
    }

    @Test
    @DisplayName("invite: delegatedScope carrying an admin role → 422 PARTNERSHIP_SCOPE_INVALID")
    void invite_adminRole_rejected() {
        assertThatThrownBy(() -> useCase().invite(HOST,
                new InvitePartnershipCommand(PARTNER, List.of("wms"), List.of("TENANT_ADMIN")),
                actor(), "collab"))
                .isInstanceOf(PartnershipScopeInvalidException.class);
        verify(partnershipPort, never()).createPending(any());
    }

    @Test
    @DisplayName("invite: self-partnership (partner == host) → VALIDATION_ERROR (IllegalArgument)")
    void invite_self_rejected() {
        assertThatThrownBy(() -> useCase().invite(HOST,
                new InvitePartnershipCommand(HOST, List.of("wms"), List.of("WMS_OP")),
                actor(), "collab"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("invite: duplicate (host,partner) → 409 PARTNERSHIP_ALREADY_EXISTS")
    void invite_duplicate_rejected() {
        when(partnershipPort.pairExists(HOST, PARTNER)).thenReturn(true);
        assertThatThrownBy(() -> useCase().invite(HOST,
                new InvitePartnershipCommand(PARTNER, List.of("wms"), List.of("WMS_OPERATOR")),
                actor(), "collab"))
                .isInstanceOf(PartnershipAlreadyExistsException.class);
        verify(partnershipPort, never()).createPending(any());
        // the duplicate short-circuits BEFORE the account-service domain check (no I/O).
        verify(subscriptionPort, never()).listActiveSubscriptions();
    }

    @Test
    @DisplayName("invite: D2 guard denies → 403 PARTNERSHIP_SCOPE_DENIED (translated)")
    void invite_scopeDenied_translated() {
        doThrow(new TenantScopeDeniedException("out of scope"))
                .when(tenantScopeGuard).requireTenantInScope(any(), any(), eq(HOST), any());
        assertThatThrownBy(() -> useCase().invite(HOST,
                new InvitePartnershipCommand(PARTNER, List.of("wms"), List.of("WMS_OPERATOR")),
                actor(), "collab"))
                .isInstanceOf(PartnershipScopeDeniedException.class);
    }

    @Test
    @DisplayName("BE-479 invite: delegated domain the host is NOT subscribed to → 422 PARTNERSHIP_SCOPE_INVALID")
    void invite_unheldDomain_rejected() {
        when(partnershipPort.pairExists(HOST, PARTNER)).thenReturn(false);
        stubHostSubscribes("wms"); // host holds wms, NOT finance
        assertThatThrownBy(() -> useCase().invite(HOST,
                new InvitePartnershipCommand(PARTNER, List.of("wms", "finance"),
                        List.of("WMS_OPERATOR", "FINANCE_OPERATOR")),
                actor(), "collab"))
                .isInstanceOf(PartnershipScopeInvalidException.class)
                .hasMessageContaining("finance");
        verify(partnershipPort, never()).createPending(any());
    }

    @Test
    @DisplayName("BE-479 invite: admin-tier domain role (WMS_ADMIN) → 422 (not delegatable, no I/O)")
    void invite_nonDelegatableRole_rejected() {
        assertThatThrownBy(() -> useCase().invite(HOST,
                new InvitePartnershipCommand(PARTNER, List.of("wms"), List.of("WMS_ADMIN")),
                actor(), "collab"))
                .isInstanceOf(PartnershipScopeInvalidException.class)
                .hasMessageContaining("WMS_ADMIN");
        verify(partnershipPort, never()).createPending(any());
        // role allowlist is local — rejected before any account-service call.
        verify(subscriptionPort, never()).listActiveSubscriptions();
    }

    @Test
    @DisplayName("BE-479 invite: account-service down at the domain check → fail-CLOSED (propagates, no partnership)")
    void invite_accountDown_failClosed() {
        when(partnershipPort.pairExists(HOST, PARTNER)).thenReturn(false);
        when(subscriptionPort.listActiveSubscriptions())
                .thenThrow(new DownstreamFailureException("account-service unavailable"));
        assertThatThrownBy(() -> useCase().invite(HOST,
                new InvitePartnershipCommand(PARTNER, List.of("wms"), List.of("WMS_OPERATOR")),
                actor(), "collab"))
                .isInstanceOf(DownstreamFailureException.class);
        verify(partnershipPort, never()).createPending(any());
    }

    // ── accept ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accept: PENDING → ACTIVE (partner side), event + transition")
    void accept_happyPath() {
        when(partnershipPort.findByPartnershipId(PID))
                .thenReturn(Optional.of(view(PartnershipStatus.PENDING)))
                .thenReturn(Optional.of(view(PartnershipStatus.ACTIVE)));

        PartnershipView result = useCase().accept(PID, PARTNER, actor(), "accepting");

        assertThat(result.status()).isEqualTo(PartnershipStatus.ACTIVE);
        verify(partnershipPort).applyTransition(eq(1L), eq(PartnershipStatus.ACTIVE), any(), any());
        verify(eventPublisher).publishAccepted(eq(PID), eq(HOST), eq(PARTNER), any(), any());
    }

    @Test
    @DisplayName("accept: acting tenant is the host (wrong side) → 404 PARTNERSHIP_NOT_FOUND")
    void accept_wrongSide_notFound() {
        when(partnershipPort.findByPartnershipId(PID))
                .thenReturn(Optional.of(view(PartnershipStatus.PENDING)));
        assertThatThrownBy(() -> useCase().accept(PID, HOST, actor(), "accepting"))
                .isInstanceOf(PartnershipNotFoundException.class);
        verify(partnershipPort, never()).applyTransition(any(Long.class), any(), any(), any());
    }

    @Test
    @DisplayName("accept: already ACTIVE → 409 PARTNERSHIP_TRANSITION_INVALID")
    void accept_alreadyActive_invalid() {
        when(partnershipPort.findByPartnershipId(PID))
                .thenReturn(Optional.of(view(PartnershipStatus.ACTIVE)));
        assertThatThrownBy(() -> useCase().accept(PID, PARTNER, actor(), "accepting"))
                .isInstanceOf(PartnershipTransitionInvalidException.class);
    }

    // ── suspend / reactivate / terminate ────────────────────────────────────────

    @Test
    @DisplayName("suspend: ACTIVE → SUSPENDED (either party), suspended event")
    void suspend_happyPath() {
        when(partnershipPort.findByPartnershipId(PID))
                .thenReturn(Optional.of(view(PartnershipStatus.ACTIVE)))
                .thenReturn(Optional.of(view(PartnershipStatus.SUSPENDED)));
        useCase().suspend(PID, HOST, actor(), "pause");
        verify(partnershipPort).applyTransition(eq(1L), eq(PartnershipStatus.SUSPENDED), any(), any());
        verify(eventPublisher).publishSuspended(eq(PID), eq(HOST), eq(PARTNER), eq("pause"), any(), any());
    }

    @Test
    @DisplayName("suspend: SUSPENDED → NO_OP (200, no event, no transition, still audited)")
    void suspend_noop() {
        when(partnershipPort.findByPartnershipId(PID))
                .thenReturn(Optional.of(view(PartnershipStatus.SUSPENDED)));
        useCase().suspend(PID, PARTNER, actor(), "pause");
        verify(partnershipPort, never()).applyTransition(any(Long.class), any(), any(), any());
        verify(eventPublisher, never()).publishSuspended(any(), any(), any(), any(), any(), any());
        verify(auditor).recordWithPermission(any(), eq(Permission.PARTNERSHIP_MANAGE));
    }

    @Test
    @DisplayName("terminate: ACTIVE → TERMINATED one-shot event with participantCountAtTermination")
    void terminate_oneShotEvent() {
        when(partnershipPort.findByPartnershipId(PID))
                .thenReturn(Optional.of(view(PartnershipStatus.ACTIVE)))
                .thenReturn(Optional.of(view(PartnershipStatus.TERMINATED)));
        when(partnershipPort.countParticipants(1L)).thenReturn(3);

        useCase().terminate(PID, HOST, actor(), "end");

        verify(partnershipPort).applyTransition(eq(1L), eq(PartnershipStatus.TERMINATED), any(), any());
        verify(eventPublisher).publishTerminated(
                eq(PID), eq(HOST), eq(PARTNER), eq("ACTIVE"), eq("end"), eq(3), any(), any());
    }

    @Test
    @DisplayName("reactivate: ACTIVE (not SUSPENDED) → 409 PARTNERSHIP_TRANSITION_INVALID")
    void reactivate_wrongState_invalid() {
        when(partnershipPort.findByPartnershipId(PID))
                .thenReturn(Optional.of(view(PartnershipStatus.ACTIVE)));
        assertThatThrownBy(() -> useCase().reactivate(PID, HOST, actor(), "resume"))
                .isInstanceOf(PartnershipTransitionInvalidException.class);
    }

    @Test
    @DisplayName("terminate: already TERMINATED → idempotent NO_OP (no event, no transition)")
    void terminate_idempotent() {
        when(partnershipPort.findByPartnershipId(PID))
                .thenReturn(Optional.of(view(PartnershipStatus.TERMINATED)));
        useCase().terminate(PID, HOST, actor(), "end");
        verify(partnershipPort, never()).applyTransition(any(Long.class), any(), any(), any());
        verify(eventPublisher, never()).publishTerminated(
                any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }
}
