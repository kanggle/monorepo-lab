package com.example.admin.application;

import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.OperatorInvitationNotFoundException;
import com.example.admin.application.exception.OperatorInvitationNotPendingException;
import com.example.admin.application.exception.RoleGrantForbiddenException;
import com.example.admin.application.exception.TenantNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorInvitationMailPort;
import com.example.admin.application.port.OperatorInvitationMailPort.DeliveryStatus;
import com.example.admin.application.port.OperatorInvitationPort;
import com.example.admin.application.port.OperatorInvitationPort.InvitationView;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.domain.rbac.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TASK-MONO-772 S2 — the management use case (admin-api.md § Operator Invitation): the contract's order, the
 * D2 / D3 refusals writing nothing, «mail after commit · failure is not an error», and the 404-for-out-of-scope rule.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OperatorInvitationUseCase — 운영자 초대 관리 (TASK-MONO-772 S2)")
class OperatorInvitationUseCaseTest {

    private static final OperatorContext ACTOR = new OperatorContext("op-admin-x", "jti-1");
    private static final String TENANT = "acme-corp";
    private static final String INV = "0199de70-0000-7000-8000-00000000inv1";

    @Mock OperatorInvitationWriter writer;
    @Mock OperatorInvitationPort invitationPort;
    @Mock OperatorInvitationMailPort mailPort;
    @Mock AdminOperatorPort operatorPort;
    @Mock TenantProvisioningPort provisioningPort;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock TenantScopeResolver tenantScopeResolver;
    @Mock RoleGrantGuard roleGrantGuard;
    @Mock AdminActionAuditor auditor;
    @InjectMocks OperatorInvitationUseCase useCase;

    private static InvitationView view(String status, Instant expiresAt, int version, String deliveryStatus) {
        return new InvitationView(7L, INV, TENANT, "person@example.com", "홍길동", List.of("SUPPORT_LOCK"), status,
                expiresAt, 11L, "op-admin-x", deliveryStatus, deliveryStatus == null ? null : Instant.EPOCH,
                null, null, null, Instant.EPOCH, version, "a".repeat(64));
    }

    private static AdminOperatorPort.OperatorView operator(String tenantId) {
        return new AdminOperatorPort.OperatorView(11L, "op-admin-x", tenantId, "admin@acme.example", null,
                "김관리", "ACTIVE", null, null, Instant.EPOCH, Instant.EPOCH, null, null);
    }

    private static Map<String, AdminOperatorPort.RoleView> supportLock() {
        Map<String, AdminOperatorPort.RoleView> m = new LinkedHashMap<>();
        m.put("SUPPORT_LOCK", new AdminOperatorPort.RoleView(3L, "SUPPORT_LOCK", "", false));
        return m;
    }

    private static CreateOperatorInvitationCommand cmd(String tenantId, String key) {
        return new CreateOperatorInvitationCommand(ACTOR, "onboarding", key, "  Person@Example.com ", "홍길동",
                List.of("SUPPORT_LOCK"), tenantId);
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("성공: 범위 → 역할 → 키 → 테넌트 → 쓰기(커밋) → 메일(원문 토큰) → delivery 기록 → 201 item + auditId")
        void happyPath() {
            given(operatorPort.resolveRolesByName(List.of("SUPPORT_LOCK"))).willReturn(supportLock());
            given(auditor.isIdempotencyKeyUsed("op-admin-x", ActionCode.OPERATOR_INVITATION_CREATE, "k-1"))
                    .willReturn(false);
            InvitationView pending = view("PENDING", Instant.now().plus(Duration.ofDays(7)), 0, null);
            given(writer.createPending(eq(ACTOR), eq(TENANT), eq("person@example.com"), eq("홍길동"),
                    eq(List.of("SUPPORT_LOCK")), eq("k-1"), eq("onboarding")))
                    .willReturn(new OperatorInvitationWriter.Issued(pending, "raw-token-1", "audit-1"));
            given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(operator(TENANT)));
            given(mailPort.send(any())).willReturn(DeliveryStatus.SENT);
            given(invitationPort.findByInvitationId(INV))
                    .willReturn(Optional.of(view("PENDING", pending.expiresAt(), 0, "SENT")));

            OperatorInvitationResult result = useCase.create(cmd(TENANT, "k-1"));

            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.expired()).isFalse();
            assertThat(result.deliveryStatus()).isEqualTo("SENT");
            assertThat(result.auditId()).isEqualTo("audit-1");
            verify(tenantScopeGuard).requireTenantInScope(
                    ACTOR, Permission.OPERATOR_MANAGE, TENANT, ActionCode.OPERATOR_INVITATION_CREATE);
            verify(provisioningPort).get(TENANT);
            ArgumentCaptor<OperatorInvitationMailPort.InvitationMail> mail =
                    ArgumentCaptor.forClass(OperatorInvitationMailPort.InvitationMail.class);
            verify(mailPort).send(mail.capture());
            assertThat(mail.getValue().token()).isEqualTo("raw-token-1");
            assertThat(mail.getValue().to()).isEqualTo("person@example.com");
            assertThat(mail.getValue().inviterDisplayName()).isEqualTo("김관리");
            assertThat(mail.getValue().toString()).doesNotContain("raw-token-1").doesNotContain("person@");
            verify(invitationPort).recordDelivery(eq(7L), eq("a".repeat(64)), eq("SENT"), any());
        }

        @Test
        @DisplayName("🔴 '*' → 400 VALIDATION_ERROR — 범위·역할·쓰기·메일 모두 없음")
        void platformTenant_refused() {
            assertThatThrownBy(() -> useCase.create(cmd("*", "k-1")))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(tenantScopeGuard, writer, mailPort, provisioningPort);
        }

        @Test
        @DisplayName("Idempotency-Key 없음 → 400 (본문 검증보다 먼저)")
        void missingKey() {
            assertThatThrownBy(() -> useCase.create(new CreateOperatorInvitationCommand(
                    ACTOR, "r", null, "not-an-email", "", null, "*")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Idempotency-Key");
            verifyNoInteractions(writer);
        }

        @Test
        @DisplayName("🔴 D2: 범위 밖 테넌트 → 403, 역할 해석·쓰기 없음")
        void outOfScope() {
            willThrow(new TenantScopeDeniedException("no")).given(tenantScopeGuard).requireTenantInScope(
                    ACTOR, Permission.OPERATOR_MANAGE, "other-corp", ActionCode.OPERATOR_INVITATION_CREATE);
            assertThatThrownBy(() -> useCase.create(cmd("other-corp", "k-1")))
                    .isInstanceOf(TenantScopeDeniedException.class);
            verify(operatorPort, never()).resolveRolesByName(anyList());
            verifyNoInteractions(writer, mailPort);
        }

        @Test
        @DisplayName("🔴 D3: 부여 메뉴 밖 역할 → 403 ROLE_GRANT_FORBIDDEN, 키 확인·쓰기 없음")
        void escalation() {
            given(operatorPort.resolveRolesByName(List.of("SUPPORT_LOCK"))).willReturn(supportLock());
            willThrow(new RoleGrantForbiddenException("no")).given(roleGrantGuard)
                    .requireGrantable(eq(ACTOR), anyCollection(), eq(ActionCode.OPERATOR_INVITATION_CREATE));
            assertThatThrownBy(() -> useCase.create(cmd(TENANT, "k-1")))
                    .isInstanceOf(RoleGrantForbiddenException.class);
            verifyNoInteractions(writer, mailPort, provisioningPort);
        }

        @Test
        @DisplayName("같은 키 재사용 → 409, 테넌트 확인·쓰기 없음")
        void keyReuse() {
            given(operatorPort.resolveRolesByName(List.of("SUPPORT_LOCK"))).willReturn(supportLock());
            given(auditor.isIdempotencyKeyUsed("op-admin-x", ActionCode.OPERATOR_INVITATION_CREATE, "k-1"))
                    .willReturn(true);
            assertThatThrownBy(() -> useCase.create(cmd(TENANT, "k-1")))
                    .isInstanceOf(IdempotencyKeyConflictException.class);
            verifyNoInteractions(writer, provisioningPort);
        }

        @Test
        @DisplayName("미등록 테넌트 → 404, 쓰기 없음 (없는 테넌트로 가는 초대를 남기지 않는다)")
        void unknownTenant() {
            given(operatorPort.resolveRolesByName(List.of("SUPPORT_LOCK"))).willReturn(supportLock());
            given(auditor.isIdempotencyKeyUsed(anyString(), any(), anyString())).willReturn(false);
            given(provisioningPort.get(TENANT)).willThrow(new TenantNotFoundException(TENANT));
            assertThatThrownBy(() -> useCase.create(cmd(TENANT, "k-1")))
                    .isInstanceOf(TenantNotFoundException.class);
            verifyNoInteractions(writer, mailPort);
        }

        @Test
        @DisplayName("메일 실패는 오류가 아니다 — 201 + delivery=FAILED_TRANSIENT, 포트가 던져도 같다")
        void mailFailure_isNotAnError() {
            given(operatorPort.resolveRolesByName(List.of("SUPPORT_LOCK"))).willReturn(supportLock());
            given(auditor.isIdempotencyKeyUsed(anyString(), any(), anyString())).willReturn(false);
            InvitationView pending = view("PENDING", Instant.now().plus(Duration.ofDays(7)), 0, null);
            given(writer.createPending(any(), anyString(), anyString(), anyString(), anyList(), anyString(), anyString()))
                    .willReturn(new OperatorInvitationWriter.Issued(pending, "raw", "audit-1"));
            given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(operator(TENANT)));
            given(mailPort.send(any())).willThrow(new IllegalStateException("port broke its contract"));
            given(invitationPort.findByInvitationId(INV))
                    .willReturn(Optional.of(view("PENDING", pending.expiresAt(), 0, "FAILED_TRANSIENT")));

            OperatorInvitationResult result = useCase.create(cmd(TENANT, "k-1"));

            assertThat(result.deliveryStatus()).isEqualTo("FAILED_TRANSIENT");
            verify(invitationPort).recordDelivery(eq(7L), anyString(), eq("FAILED_TRANSIENT"), any());
        }
    }

    @Nested
    @DisplayName("list")
    class ListInvitations {

        @Test
        @DisplayName("🔴 비-플랫폼 운영자가 effective scope 밖 tenantId → 403, 조회 없음")
        void nonPlatform_outOfScope() {
            given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(operator(TENANT)));
            given(tenantScopeResolver.resolveEffectiveTenantScope(11L, TENANT)).willReturn(Set.of(TENANT));
            assertThatThrownBy(() -> useCase.list(ACTOR, "other-corp", null, 0, 20))
                    .isInstanceOf(TenantScopeDeniedException.class);
            verify(invitationPort, never()).findPage(any(), anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("생략 → 자기 home 테넌트 · 기본 status=PENDING · 만료된 PENDING 은 expired=true")
        void defaults_andExpiredFlag() {
            given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(operator(TENANT)));
            given(tenantScopeResolver.resolveEffectiveTenantScope(11L, TENANT)).willReturn(Set.of(TENANT));
            given(invitationPort.findPage(TENANT, "PENDING", 0, 20)).willReturn(new OperatorInvitationPort.InvitationPage(
                    List.of(view("PENDING", Instant.now().minusSeconds(1), 0, "SENT")), 1, 0, 20, 1));

            OperatorInvitationPageResult page = useCase.list(ACTOR, null, null, 0, 20);

            assertThat(page.content()).hasSize(1);
            assertThat(page.content().get(0).expired()).isTrue();
        }

        @Test
        @DisplayName("플랫폼 운영자 + '*' → 전 테넌트 (findPage(null, …))")
        void platform_allTenants() {
            given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(operator("*")));
            given(invitationPort.findPage(null, "ACCEPTED", 1, 5))
                    .willReturn(new OperatorInvitationPort.InvitationPage(List.of(), 0, 1, 5, 0));
            assertThat(useCase.list(ACTOR, "*", "accepted", 1, 5).content()).isEmpty();
        }

        @Test
        @DisplayName("status 오류 · size > 100 → 400")
        void badParams() {
            assertThatThrownBy(() -> useCase.list(ACTOR, null, "EXPIRED", 0, 20))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> useCase.list(ACTOR, null, null, 0, 101))
                    .isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(invitationPort);
        }
    }

    @Nested
    @DisplayName("cancel / resend")
    class CancelResend {

        @Test
        @DisplayName("🔴 범위 밖 테넌트의 초대 → 404 (없는 것과 같은 답), 쓰기 없음")
        void outOfScope_is404() {
            given(invitationPort.findByInvitationId(INV)).willReturn(Optional.of(view("PENDING", Instant.MAX, 0, null)));
            given(tenantScopeGuard.isTenantInScope(ACTOR, Permission.OPERATOR_MANAGE, TENANT)).willReturn(false);
            assertThatThrownBy(() -> useCase.cancel(ACTOR, INV, "r"))
                    .isInstanceOf(OperatorInvitationNotFoundException.class);
            assertThatThrownBy(() -> useCase.resend(ACTOR, INV, "r"))
                    .isInstanceOf(OperatorInvitationNotFoundException.class);
            verifyNoInteractions(writer, mailPort);
        }

        @Test
        @DisplayName("이미 CANCELLED → 200 no-op (writer 호출 없음 = 감사 행 없음) · ACCEPTED → 409")
        void cancel_terminalStates() {
            given(tenantScopeGuard.isTenantInScope(ACTOR, Permission.OPERATOR_MANAGE, TENANT)).willReturn(true);
            given(invitationPort.findByInvitationId(INV))
                    .willReturn(Optional.of(view("CANCELLED", Instant.MAX, 1, null)))
                    .willReturn(Optional.of(view("ACCEPTED", Instant.MAX, 1, null)));

            assertThat(useCase.cancel(ACTOR, INV, "r").status()).isEqualTo("CANCELLED");
            assertThatThrownBy(() -> useCase.cancel(ACTOR, INV, "r"))
                    .isInstanceOf(OperatorInvitationNotPendingException.class);
            verifyNoInteractions(writer);
        }

        @Test
        @DisplayName("PENDING(만료 포함) 취소 → writer.cancel")
        void cancel_pending() {
            InvitationView expiredPending = view("PENDING", Instant.now().minusSeconds(60), 0, null);
            given(tenantScopeGuard.isTenantInScope(ACTOR, Permission.OPERATOR_MANAGE, TENANT)).willReturn(true);
            given(invitationPort.findByInvitationId(INV))
                    .willReturn(Optional.of(expiredPending))
                    .willReturn(Optional.of(view("CANCELLED", expiredPending.expiresAt(), 1, null)));
            given(writer.cancel(ACTOR, expiredPending, "r")).willReturn(true);

            assertThat(useCase.cancel(ACTOR, INV, "r").status()).isEqualTo("CANCELLED");
        }

        @Test
        @DisplayName("resend: 재발송 actor 기준 D3 위반 → 403, 토큰 회전 없음")
        void resend_escalation() {
            given(tenantScopeGuard.isTenantInScope(ACTOR, Permission.OPERATOR_MANAGE, TENANT)).willReturn(true);
            given(invitationPort.findByInvitationId(INV)).willReturn(Optional.of(view("PENDING", Instant.MAX, 0, null)));
            given(operatorPort.resolveRolesByName(List.of("SUPPORT_LOCK"))).willReturn(supportLock());
            willThrow(new RoleGrantForbiddenException("no")).given(roleGrantGuard)
                    .requireGrantable(eq(ACTOR), anyCollection(), eq(ActionCode.OPERATOR_INVITATION_RESEND));
            assertThatThrownBy(() -> useCase.resend(ACTOR, INV, "r")).isInstanceOf(RoleGrantForbiddenException.class);
            verifyNoInteractions(writer, mailPort, provisioningPort);
        }

        @Test
        @DisplayName("resend: CANCELLED → 409")
        void resend_notPending() {
            given(tenantScopeGuard.isTenantInScope(ACTOR, Permission.OPERATOR_MANAGE, TENANT)).willReturn(true);
            given(invitationPort.findByInvitationId(INV)).willReturn(Optional.of(view("CANCELLED", Instant.MAX, 1, null)));
            assertThatThrownBy(() -> useCase.resend(ACTOR, INV, "r"))
                    .isInstanceOf(OperatorInvitationNotPendingException.class);
            verifyNoInteractions(writer);
        }

        @Test
        @DisplayName("resend 성공: 새 토큰이 메일로, delivery 는 새 토큰의 해시에 기록")
        void resend_ok() {
            InvitationView before = view("PENDING", Instant.now().minusSeconds(5), 0, "FAILED_TRANSIENT");
            InvitationView after = new InvitationView(7L, INV, TENANT, "person@example.com", "홍길동",
                    List.of("SUPPORT_LOCK"), "PENDING", Instant.now().plus(Duration.ofDays(7)), 11L, "op-admin-x",
                    null, null, null, null, null, Instant.EPOCH, 1, "b".repeat(64));
            given(tenantScopeGuard.isTenantInScope(ACTOR, Permission.OPERATOR_MANAGE, TENANT)).willReturn(true);
            given(invitationPort.findByInvitationId(INV)).willReturn(Optional.of(before));
            given(operatorPort.resolveRolesByName(List.of("SUPPORT_LOCK"))).willReturn(supportLock());
            given(writer.rotate(ACTOR, before, "r")).willReturn(new OperatorInvitationWriter.Issued(after, "raw-2", "a-2"));
            given(operatorPort.findByOperatorId("op-admin-x")).willReturn(Optional.of(operator(TENANT)));
            given(mailPort.send(any())).willReturn(DeliveryStatus.FAILED_PERMANENT);

            OperatorInvitationResult result = useCase.resend(ACTOR, INV, "r");

            assertThat(result.status()).isEqualTo("PENDING");
            verify(invitationPort).recordDelivery(eq(7L), eq("b".repeat(64)), eq("FAILED_PERMANENT"), any());
            verify(provisioningPort).get(TENANT);
            verify(invitationPort, never()).rotateIfPending(anyLong(), anyInt(), anyString(), any(), anyLong(), any());
        }
    }
}
