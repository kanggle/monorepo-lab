package com.example.erp.approval.application;

import com.example.erp.approval.application.DelegationApplicationService.DelegationRole;
import com.example.erp.approval.application.command.Commands.CreateDelegationCommand;
import com.example.erp.approval.application.command.Commands.RevokeDelegationCommand;
import com.example.erp.approval.application.event.ApprovalEventPublisher;
import com.example.erp.approval.application.port.outbound.ClockPort;
import com.example.erp.approval.domain.audit.ApprovalAuditLog;
import com.example.erp.approval.domain.audit.ApprovalAuditLogRepository;
import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.delegation.DelegationGrantRepository;
import com.example.erp.approval.domain.delegation.DelegationScope;
import com.example.erp.approval.domain.delegation.DelegationStatus;
import com.example.erp.approval.domain.error.ApprovalErrors.DelegationInvalidException;
import com.example.erp.approval.domain.error.ApprovalErrors.DelegationNotFoundException;
import com.example.erp.approval.domain.error.ApprovalErrors.PermissionDeniedException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Application unit tests for {@link DelegationApplicationService} (TASK-ERP-BE-013):
 * create → grant saved + delegated event + audit row (same Tx); revoke → REVOKED
 * + audit + NO event + idempotent; 404 on unknown revoke; list role filter; authz.
 * {@code STRICT_STUBS}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DelegationApplicationServiceTest {

    private static final String TENANT = "erp";
    private static final Instant NOW = Instant.parse("2026-06-05T00:00:00Z");
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-06-30T00:00:00Z");
    private static final ActorContext A = new ActorContext("emp-a", TENANT,
            Set.of("erp.write", "erp.read"), Set.of("*"));
    private static final ActorContext NO_ROLE = new ActorContext("emp-x", TENANT,
            Set.of(), Set.of());

    @Mock DelegationGrantRepository repo;
    @Mock ApprovalAuditLogRepository auditLogRepository;
    @Mock ApprovalEventPublisher eventPublisher;
    @Mock ClockPort clock;

    DelegationApplicationService service;

    @BeforeEach
    void setup() {
        service = new DelegationApplicationService(repo, auditLogRepository, eventPublisher, clock);
        lenient().when(clock.now()).thenReturn(NOW);
        lenient().when(repo.save(any(DelegationGrant.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(auditLogRepository.append(any(ApprovalAuditLog.class)))
                .thenAnswer(i -> i.getArgument(0));
    }

    private DelegationGrant grant() {
        return DelegationGrant.create("dgr-1", TENANT, "emp-a", "emp-d", FROM, TO,
                "vacation", DelegationScope.GLOBAL, null, "emp-a", FROM);
    }

    @Test
    @DisplayName("create → grant saved + delegated event + audit row (GLOBAL default)")
    void create() {
        var view = service.createDelegation(new CreateDelegationCommand(
                A, "emp-d", FROM, TO, "vacation", DelegationScope.GLOBAL, null));
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.delegatorId()).isEqualTo("emp-a");
        assertThat(view.delegateId()).isEqualTo("emp-d");
        assertThat(view.scope()).isEqualTo("GLOBAL");
        assertThat(view.scopeRequestId()).isNull();
        verify(repo).save(any(DelegationGrant.class));
        verify(auditLogRepository).append(any(ApprovalAuditLog.class));
        verify(eventPublisher).publishDelegated(any(DelegationGrant.class), any());
    }

    @Test
    @DisplayName("create REQUEST-scoped grant → ACTIVE + scope=REQUEST + scopeRequestId")
    void createRequestScoped() {
        var view = service.createDelegation(new CreateDelegationCommand(
                A, "emp-d", FROM, TO, "cover R1", DelegationScope.REQUEST, "appr-1"));
        assertThat(view.status()).isEqualTo("ACTIVE");
        assertThat(view.scope()).isEqualTo("REQUEST");
        assertThat(view.scopeRequestId()).isEqualTo("appr-1");
        verify(repo).save(any(DelegationGrant.class));
        verify(eventPublisher).publishDelegated(any(DelegationGrant.class), any());
    }

    @Test
    @DisplayName("create REQUEST scope with no scopeRequestId → DELEGATION_INVALID (422, no save/event)")
    void createRequestScopeCoherenceViolation() {
        assertThatThrownBy(() -> service.createDelegation(new CreateDelegationCommand(
                A, "emp-d", FROM, TO, null, DelegationScope.REQUEST, null)))
                .isInstanceOf(DelegationInvalidException.class);
        verify(repo, never()).save(any());
        verify(eventPublisher, never()).publishDelegated(any(), any());
    }

    @Test
    @DisplayName("create GLOBAL scope with a scopeRequestId → DELEGATION_INVALID (422)")
    void createGlobalScopeWithRequestId() {
        assertThatThrownBy(() -> service.createDelegation(new CreateDelegationCommand(
                A, "emp-d", FROM, TO, null, DelegationScope.GLOBAL, "appr-1")))
                .isInstanceOf(DelegationInvalidException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("create self-delegation → DELEGATION_INVALID (no save/event)")
    void createSelfDelegation() {
        assertThatThrownBy(() -> service.createDelegation(new CreateDelegationCommand(
                A, "emp-a", FROM, TO, null, DelegationScope.GLOBAL, null)))
                .isInstanceOf(DelegationInvalidException.class);
        verify(repo, never()).save(any());
        verify(eventPublisher, never()).publishDelegated(any(), any());
    }

    @Test
    @DisplayName("create invalid window → DELEGATION_INVALID")
    void createInvalidWindow() {
        assertThatThrownBy(() -> service.createDelegation(new CreateDelegationCommand(
                A, "emp-d", TO, FROM, null, DelegationScope.GLOBAL, null)))
                .isInstanceOf(DelegationInvalidException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("create without write role → PermissionDenied (no save)")
    void createNoRole() {
        assertThatThrownBy(() -> service.createDelegation(new CreateDelegationCommand(
                NO_ROLE, "emp-d", FROM, TO, null, DelegationScope.GLOBAL, null)))
                .isInstanceOf(PermissionDeniedException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("revoke → REVOKED + audit + revoked event (TASK-ERP-BE-015), no delegated event")
    void revoke() {
        when(repo.findById("dgr-1", TENANT)).thenReturn(Optional.of(grant()));
        var view = service.revokeDelegation(new RevokeDelegationCommand(A, "dgr-1", "no longer away"));
        assertThat(view.status()).isEqualTo("REVOKED");
        verify(repo).save(any(DelegationGrant.class));
        verify(auditLogRepository).append(any(ApprovalAuditLog.class));
        // TASK-ERP-BE-015: an actual ACTIVE→REVOKED transition emits the revoke event.
        verify(eventPublisher).publishRevoked(any(DelegationGrant.class), eq("emp-a"));
        verify(eventPublisher, never()).publishDelegated(any(), any());
    }

    @Test
    @DisplayName("revoke idempotent: already REVOKED → no second audit, no save, NO revoke event")
    void revokeIdempotent() {
        DelegationGrant already = grant();
        already.revoke("emp-a", NOW);
        when(repo.findById("dgr-1", TENANT)).thenReturn(Optional.of(already));
        var view = service.revokeDelegation(new RevokeDelegationCommand(A, "dgr-1", "again"));
        assertThat(view.status()).isEqualTo("REVOKED");
        verify(repo, never()).save(any());
        verify(auditLogRepository, never()).append(any());
        // Idempotent re-revoke is not a transition → no re-emission (transition-once).
        verify(eventPublisher, never()).publishRevoked(any(), any());
    }

    @Test
    @DisplayName("revoke unknown id → DELEGATION_NOT_FOUND")
    void revokeNotFound() {
        when(repo.findById("missing", TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.revokeDelegation(
                new RevokeDelegationCommand(A, "missing", "x")))
                .isInstanceOf(DelegationNotFoundException.class);
    }

    @Test
    @DisplayName("list (no role) → grants as delegator OR delegate")
    void listBoth() {
        when(repo.findByDelegatorOrDelegate("emp-a", TENANT)).thenReturn(List.of(grant()));
        var rows = service.listDelegations(A, null);
        assertThat(rows).hasSize(1);
        verify(repo).findByDelegatorOrDelegate("emp-a", TENANT);
    }

    @Test
    @DisplayName("list role=DELEGATOR → delegator-only query")
    void listDelegator() {
        when(repo.findByDelegator("emp-a", TENANT)).thenReturn(List.of(grant()));
        var rows = service.listDelegations(A, DelegationRole.DELEGATOR);
        assertThat(rows).hasSize(1);
        verify(repo).findByDelegator("emp-a", TENANT);
    }

    @Test
    @DisplayName("list role=DELEGATE → delegate-only query")
    void listDelegate() {
        when(repo.findByDelegate("emp-a", TENANT)).thenReturn(List.of());
        var rows = service.listDelegations(A, DelegationRole.DELEGATE);
        assertThat(rows).isEmpty();
        verify(repo).findByDelegate("emp-a", TENANT);
    }

    @Test
    @DisplayName("parseRole maps the contract filter string")
    void parseRole() {
        assertThat(DelegationApplicationService.parseRole("DELEGATOR")).isEqualTo(DelegationRole.DELEGATOR);
        assertThat(DelegationApplicationService.parseRole("delegate")).isEqualTo(DelegationRole.DELEGATE);
        assertThat(DelegationApplicationService.parseRole(null)).isNull();
        assertThat(DelegationApplicationService.parseRole("  ")).isNull();
        // ensure the enum's only-source reference is exercised
        assertThat(DelegationStatus.values()).contains(DelegationStatus.ACTIVE);
    }
}
