package com.example.admin.application;

import com.example.admin.application.event.PartnershipEventPublisher;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.ParticipantNotFoundException;
import com.example.admin.application.exception.ParticipantNotOwnOperatorException;
import com.example.admin.application.exception.ParticipantScopeExceedsDelegationException;
import com.example.admin.application.exception.PartnershipNotFoundException;
import com.example.admin.application.exception.PartnershipTransitionInvalidException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.application.port.TenantPartnershipPort.ParticipantView;
import com.example.admin.application.port.TenantPartnershipPort.PartnershipView;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-477 / ADR-MONO-045 D4 — unit tests for
 * {@link ManagePartnershipParticipantUseCase}: own-operator check, participant-scope
 * subset check, ACTIVE-only guard, offboarding removal.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ManagePartnershipParticipantUseCaseTest {

    private static final String HOST = "acme-corp";
    private static final String PARTNER = "globex";
    private static final String PID = "00000000-0000-7000-8000-00000000p001";
    private static final String OP_UUID = "00000000-0000-7000-8000-0000000000b1";

    @Mock TenantPartnershipPort partnershipPort;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock AdminActionAuditor auditor;
    @Mock PartnershipEventPublisher eventPublisher;
    @Mock AdminOperatorPort operatorPort;

    private ManagePartnershipParticipantUseCase useCase() {
        return new ManagePartnershipParticipantUseCase(partnershipPort, tenantScopeGuard, auditor,
                eventPublisher, operatorPort);
    }

    private OperatorContext actor() {
        return new OperatorContext("op-partner", "jti-2");
    }

    private PartnershipView partnership(PartnershipStatus status) {
        return new PartnershipView(1L, PID, HOST, PARTNER, status,
                ScopeSet.of(List.of("wms", "scm"), List.of("WMS_OP", "SCM_PLANNER")),
                null, null, Instant.now(), Instant.now(), null);
    }

    private AdminOperatorPort.OperatorView operator(String homeTenant, long internalId) {
        return new AdminOperatorPort.OperatorView(
                internalId, OP_UUID, homeTenant, "b@example.com", "hash", "B Op",
                "ACTIVE", null, null, Instant.now(), Instant.now(), null, null);
    }

    // ── add ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("add: partner-owned operator, subset scope → addParticipant + event")
    void add_happyPath() {
        when(partnershipPort.findByPartnershipId(PID)).thenReturn(Optional.of(partnership(PartnershipStatus.ACTIVE)));
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(operator(PARTNER, 42L)));

        useCase().addParticipant(PID, OP_UUID, PARTNER, List.of("wms"), List.of("WMS_OP"), actor(), "assign");

        verify(partnershipPort).addParticipant(eq(1L), eq(42L), any(), any(), any());
        verify(eventPublisher).publishParticipantAdded(eq(PID), eq(HOST), eq(PARTNER), eq(OP_UUID), any(), any(), any());
        verify(auditor).recordWithPermission(any(), eq(Permission.PARTNERSHIP_MANAGE));
    }

    @Test
    @DisplayName("add: operator home tenant != partner → 422 PARTICIPANT_NOT_OWN_OPERATOR")
    void add_notOwnOperator() {
        when(partnershipPort.findByPartnershipId(PID)).thenReturn(Optional.of(partnership(PartnershipStatus.ACTIVE)));
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(operator("initech", 42L)));

        assertThatThrownBy(() -> useCase().addParticipant(PID, OP_UUID, PARTNER, null, null, actor(), "assign"))
                .isInstanceOf(ParticipantNotOwnOperatorException.class);
        verify(partnershipPort, never()).addParticipant(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("add: participantScope not a subset of delegatedScope → 422 PARTICIPANT_SCOPE_EXCEEDS_DELEGATION")
    void add_scopeExceeds() {
        when(partnershipPort.findByPartnershipId(PID)).thenReturn(Optional.of(partnership(PartnershipStatus.ACTIVE)));
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(operator(PARTNER, 42L)));

        assertThatThrownBy(() -> useCase().addParticipant(
                PID, OP_UUID, PARTNER, List.of("finance"), List.of("WMS_OP"), actor(), "assign"))
                .isInstanceOf(ParticipantScopeExceedsDelegationException.class);
        verify(partnershipPort, never()).addParticipant(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("add: non-ACTIVE partnership → 409 PARTNERSHIP_TRANSITION_INVALID")
    void add_nonActive() {
        when(partnershipPort.findByPartnershipId(PID)).thenReturn(Optional.of(partnership(PartnershipStatus.PENDING)));
        assertThatThrownBy(() -> useCase().addParticipant(PID, OP_UUID, PARTNER, null, null, actor(), "assign"))
                .isInstanceOf(PartnershipTransitionInvalidException.class);
    }

    @Test
    @DisplayName("add: acting tenant is host (not partner) → 404 PARTNERSHIP_NOT_FOUND")
    void add_wrongSide() {
        when(partnershipPort.findByPartnershipId(PID)).thenReturn(Optional.of(partnership(PartnershipStatus.ACTIVE)));
        assertThatThrownBy(() -> useCase().addParticipant(PID, OP_UUID, HOST, null, null, actor(), "assign"))
                .isInstanceOf(PartnershipNotFoundException.class);
    }

    @Test
    @DisplayName("add: unknown operator → 404 OPERATOR_NOT_FOUND")
    void add_operatorNotFound() {
        when(partnershipPort.findByPartnershipId(PID)).thenReturn(Optional.of(partnership(PartnershipStatus.ACTIVE)));
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase().addParticipant(PID, OP_UUID, PARTNER, null, null, actor(), "assign"))
                .isInstanceOf(OperatorNotFoundException.class);
    }

    // ── remove ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("remove: existing participant → removeParticipant + event")
    void remove_happyPath() {
        when(partnershipPort.findByPartnershipId(PID)).thenReturn(Optional.of(partnership(PartnershipStatus.ACTIVE)));
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(operator(PARTNER, 42L)));
        when(partnershipPort.findParticipant(1L, 42L))
                .thenReturn(Optional.of(new ParticipantView(1L, 42L, null, Instant.now(), null)));

        useCase().removeParticipant(PID, OP_UUID, PARTNER, actor(), "offboard");

        verify(partnershipPort).removeParticipant(1L, 42L);
        verify(eventPublisher).publishParticipantRemoved(eq(PID), eq(HOST), eq(PARTNER), eq(OP_UUID), any(), any());
    }

    @Test
    @DisplayName("remove: no participant binding → 404 PARTICIPANT_NOT_FOUND")
    void remove_notFound() {
        when(partnershipPort.findByPartnershipId(PID)).thenReturn(Optional.of(partnership(PartnershipStatus.ACTIVE)));
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(operator(PARTNER, 42L)));
        when(partnershipPort.findParticipant(1L, 42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().removeParticipant(PID, OP_UUID, PARTNER, actor(), "offboard"))
                .isInstanceOf(ParticipantNotFoundException.class);
        verify(partnershipPort, never()).removeParticipant(anyLong(), anyLong());
    }
}
