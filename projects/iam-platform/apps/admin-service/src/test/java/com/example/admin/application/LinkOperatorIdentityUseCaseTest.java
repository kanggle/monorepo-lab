package com.example.admin.application;

import com.example.admin.application.exception.AccountIdentityUnresolvableException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.IdentityLinkEmailMismatchException;
import com.example.admin.application.exception.OperatorAlreadyLinkedException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.client.AccountServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-373 / ADR-MONO-034 U3 — unit tests for the opt-in operator↔identity
 * LINK use case (Docker-free, Mockito STRICT_STUBS).
 *
 * <p>Covers the FIXED U3 design: email-match success links + audits; email-mismatch
 * → reject (no link, no audit); null/unresolvable identity → reject; idempotent
 * re-link same identity → no-op success; already-linked-to-different → reject;
 * downstream error → fail-CLOSED reject.
 */
@ExtendWith(MockitoExtension.class)
class LinkOperatorIdentityUseCaseTest {

    @Mock AdminOperatorPort operatorPort;
    @Mock AccountServiceClient accountServiceClient;
    @Mock AdminActionAuditor auditor;
    @Mock TenantScopeGuard tenantScopeGuard;

    @InjectMocks LinkOperatorIdentityUseCase useCase;

    private static final String OP_UUID = "00000000-0000-7000-8000-0000000000aa";
    private static final String ACCOUNT_ID = "acc-1111";
    private static final String TENANT = "wms";
    private static final String IDENTITY_ID = "idy-7777";
    private static final OperatorContext ACTOR = new OperatorContext("actor-uuid", "jti-1");
    private static final String REASON = "link MD dual-role identity";

    private static AccountServiceClient.AccountDetailResponse account(String email) {
        return new AccountServiceClient.AccountDetailResponse(
                ACCOUNT_ID, email, "ACTIVE", Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    @Test
    void link_email_match_sets_identity_and_audits() {
        AdminOperatorPort.OperatorView op =
                operator(10L, OP_UUID, "md@example.com", "ACTIVE", TENANT, null, null);
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(op));
        when(accountServiceClient.getDetail(ACCOUNT_ID)).thenReturn(account("MD@example.com")); // case-insensitive
        when(accountServiceClient.resolveIdentity(TENANT, ACCOUNT_ID)).thenReturn(IDENTITY_ID);

        LinkOperatorIdentityUseCase.LinkResult result =
                useCase.link(OP_UUID, ACCOUNT_ID, TENANT, ACTOR, REASON);

        assertThat(result.alreadyLinked()).isFalse();
        assertThat(result.identityId()).isEqualTo(IDENTITY_ID);

        verify(tenantScopeGuard, times(1)).requireTenantInScope(
                eq(ACTOR), eq(Permission.OPERATOR_MANAGE), eq(TENANT),
                eq(ActionCode.OPERATOR_IDENTITY_LINK));
        verify(operatorPort, times(1)).linkIdentity(eq(10L), eq(IDENTITY_ID), any(Instant.class));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        ArgumentCaptor<String> perm = ArgumentCaptor.forClass(String.class);
        verify(auditor, times(1)).recordWithPermission(rec.capture(), perm.capture());
        assertThat(rec.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_IDENTITY_LINK);
        assertThat(rec.getValue().targetId()).isEqualTo(OP_UUID);
        assertThat(rec.getValue().targetTenantId()).isEqualTo(TENANT);
        assertThat(rec.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(perm.getValue()).isEqualTo(Permission.OPERATOR_MANAGE);
    }

    @Test
    void link_email_mismatch_rejects_without_link_or_audit() {
        AdminOperatorPort.OperatorView op =
                operator(10L, OP_UUID, "operator@example.com", "ACTIVE", TENANT, null, null);
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(op));
        when(accountServiceClient.getDetail(ACCOUNT_ID)).thenReturn(account("different@example.com"));
        when(accountServiceClient.resolveIdentity(TENANT, ACCOUNT_ID)).thenReturn(IDENTITY_ID);

        assertThatThrownBy(() -> useCase.link(OP_UUID, ACCOUNT_ID, TENANT, ACTOR, REASON))
                .isInstanceOf(IdentityLinkEmailMismatchException.class);

        verify(operatorPort, never()).linkIdentity(anyLong(), anyString(), any(Instant.class));
        verify(auditor, never()).recordWithPermission(any(), anyString());
    }

    @Test
    void link_unresolvable_identity_rejects_fail_closed() {
        AdminOperatorPort.OperatorView op =
                operator(10L, OP_UUID, "md@example.com", "ACTIVE", TENANT, null, null);
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(op));
        when(accountServiceClient.getDetail(ACCOUNT_ID)).thenReturn(account("md@example.com"));
        when(accountServiceClient.resolveIdentity(TENANT, ACCOUNT_ID)).thenReturn(null); // 200 + null

        assertThatThrownBy(() -> useCase.link(OP_UUID, ACCOUNT_ID, TENANT, ACTOR, REASON))
                .isInstanceOf(AccountIdentityUnresolvableException.class);

        verify(operatorPort, never()).linkIdentity(anyLong(), anyString(), any(Instant.class));
        verify(auditor, never()).recordWithPermission(any(), anyString());
    }

    @Test
    void link_idempotent_same_identity_is_noop_success_and_audits() {
        AdminOperatorPort.OperatorView op =
                operator(10L, OP_UUID, "md@example.com", "ACTIVE", TENANT, null, IDENTITY_ID);
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(op));
        when(accountServiceClient.getDetail(ACCOUNT_ID)).thenReturn(account("md@example.com"));
        when(accountServiceClient.resolveIdentity(TENANT, ACCOUNT_ID)).thenReturn(IDENTITY_ID);

        LinkOperatorIdentityUseCase.LinkResult result =
                useCase.link(OP_UUID, ACCOUNT_ID, TENANT, ACTOR, REASON);

        assertThat(result.alreadyLinked()).isTrue();
        assertThat(result.identityId()).isEqualTo(IDENTITY_ID);
        // no-op: NO persistence write
        verify(operatorPort, never()).linkIdentity(anyLong(), anyString(), any(Instant.class));
        // but the idempotent success is still audited
        verify(auditor, times(1)).recordWithPermission(any(), eq(Permission.OPERATOR_MANAGE));
    }

    @Test
    void link_already_linked_to_different_identity_rejects() {
        AdminOperatorPort.OperatorView op =
                operator(10L, OP_UUID, "md@example.com", "ACTIVE", TENANT, null, "idy-OTHER");
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(op));
        when(accountServiceClient.getDetail(ACCOUNT_ID)).thenReturn(account("md@example.com"));
        when(accountServiceClient.resolveIdentity(TENANT, ACCOUNT_ID)).thenReturn(IDENTITY_ID);

        assertThatThrownBy(() -> useCase.link(OP_UUID, ACCOUNT_ID, TENANT, ACTOR, REASON))
                .isInstanceOf(OperatorAlreadyLinkedException.class);

        verify(operatorPort, never()).linkIdentity(anyLong(), anyString(), any(Instant.class));
        verify(auditor, never()).recordWithPermission(any(), anyString());
    }

    @Test
    void link_downstream_error_fails_closed_no_link() {
        AdminOperatorPort.OperatorView op =
                operator(10L, OP_UUID, "md@example.com", "ACTIVE", TENANT, null, null);
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.of(op));
        when(accountServiceClient.getDetail(ACCOUNT_ID))
                .thenThrow(new DownstreamFailureException("account-service unavailable"));

        assertThatThrownBy(() -> useCase.link(OP_UUID, ACCOUNT_ID, TENANT, ACTOR, REASON))
                .isInstanceOf(DownstreamFailureException.class);

        verify(operatorPort, never()).linkIdentity(anyLong(), anyString(), any(Instant.class));
        verify(auditor, never()).recordWithPermission(any(), anyString());
    }

    @Test
    void link_operator_not_found_rejects_without_downstream_call() {
        when(operatorPort.findByOperatorId(OP_UUID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.link(OP_UUID, ACCOUNT_ID, TENANT, ACTOR, REASON))
                .isInstanceOf(OperatorNotFoundException.class);

        verify(accountServiceClient, never()).getDetail(anyString());
        verify(accountServiceClient, never()).resolveIdentity(anyString(), anyString());
        verify(operatorPort, never()).linkIdentity(anyLong(), anyString(), any(Instant.class));
        verify(auditor, never()).recordWithPermission(any(), anyString());
    }
}
