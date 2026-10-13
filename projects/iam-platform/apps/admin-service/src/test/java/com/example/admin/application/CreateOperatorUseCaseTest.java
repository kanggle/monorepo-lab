package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.exception.OperatorInvitationRequiredException;
import com.example.admin.application.exception.RoleNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.security.password.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.admin.application.OperatorUseCaseTestSupport.actor;
import static com.example.admin.application.OperatorUseCaseTestSupport.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code POST /api/admin/operators} use case.
 *
 * <p>TASK-MONO-772 S7 (ADR-MONO-080 D6 · D-7): only {@code tenantId='*'} is created here; every other tenant is
 * refused with {@code 422 OPERATOR_INVITATION_REQUIRED} before anything is read, written or called. The
 * TASK-MONO-334 account-existence cells (and the TASK-BE-374 non-{@code '*'} identity-link cells, a path that only
 * ever ran for non-{@code '*'} tenants) went with that rule.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CreateOperatorUseCaseTest {

    private static final String PLATFORM = "*";

    @Mock AdminOperatorPort operatorPort;
    @Mock AdminActionAuditor auditor;
    @Mock PasswordHasher passwordHasher;
    @Mock OperatorLookupPort operatorLookupPort;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock RoleGrantGuard roleGrantGuard;

    CreateOperatorUseCase useCase;

    @BeforeEach
    void initUseCase() {
        useCase = new CreateOperatorUseCase(
                operatorPort, auditor, passwordHasher, operatorLookupPort, tenantScopeGuard, roleGrantGuard);

        // Default: actor is platform-scope (SUPER_ADMIN, '*') — the only actor that can create anything now.
        when(operatorLookupPort.findByOperatorId("actor-uuid"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorLookupRef(99L, "actor-uuid", PLATFORM)));
    }

    private void actorIsCompanyScoped() {
        when(operatorLookupPort.findByOperatorId("actor-uuid"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorLookupRef(99L, "actor-uuid", "fan-platform")));
    }

    private AdminOperatorPort.OperatorView createdView(long id, String email, String displayName) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new AdminOperatorPort.OperatorView(
                id, "new-op-uuid", PLATFORM, email, "hash-value", displayName, "ACTIVE",
                null, null, now, now, null, null);
    }

    private void stubHappyCreate(String email, long id) {
        when(operatorPort.existsByTenantIdAndEmail(PLATFORM, email)).thenReturn(false);
        when(operatorPort.resolveRolesByName(anyList())).thenReturn(new LinkedHashMap<>());
        when(passwordHasher.hash(anyString())).thenReturn("h");
        when(operatorPort.createOperator(any(AdminOperatorPort.NewOperator.class)))
                .thenReturn(createdView(id, email, "Op"));
        when(operatorPort.resolveActorInternalId("actor-uuid")).thenReturn(99L);
        when(auditor.newAuditId()).thenReturn("audit-" + id);
    }

    // ── TASK-MONO-772 S7: company tenants are invitation-only ───────────────────

    @ParameterizedTest(name = "tenantId={0}")
    @ValueSource(strings = {"fan-platform", "ecommerce", "demo-corp", "iam"})
    @DisplayName("🔴 772 S7: 비-'*' 테넌트 → 422 OPERATOR_INVITATION_REQUIRED — 읽기 · 쓰기 · 감사 · 범위 검사 · 하류 호출 0 (SUPER_ADMIN 이어도)")
    void createOperator_companyTenant_refused_beforeAnything(String tenantId) {
        assertThatThrownBy(() -> useCase.createOperator(
                "staff@example.com", "Staff", "StrongPass1!",
                List.of("SUPPORT_LOCK"), actor(), "provisioning", tenantId))
                .isInstanceOf(OperatorInvitationRequiredException.class)
                .satisfies(e -> {
                    OperatorInvitationRequiredException ex = (OperatorInvitationRequiredException) e;
                    assertThat(ex.getCode()).isEqualTo("OPERATOR_INVITATION_REQUIRED");
                    assertThat(ex.getHttpStatus()).isEqualTo(422);
                    // No address or tenant value echoed into the message.
                    assertThat(ex.getMessage()).doesNotContain("staff@example.com").doesNotContain(tenantId);
                });

        verifyNoInteractions(operatorPort, auditor, passwordHasher, operatorLookupPort, tenantScopeGuard,
                roleGrantGuard);
    }

    @Test
    @DisplayName("🔴 772 S7: 자기 회사 테넌트에 만들려는 회사 운영자도 422 — actor 와 무관(403 이 아니다)")
    void createOperator_companyActor_ownTenant_alsoInvitationRequired() {
        actorIsCompanyScoped();

        assertThatThrownBy(() -> useCase.createOperator(
                "staff@example.com", "Staff", null, List.of(), actor(), "provisioning", "fan-platform"))
                .isInstanceOf(OperatorInvitationRequiredException.class);

        verify(auditor, never()).recordCrossTenantDenied(any(), anyString(), any(), anyString(), anyString());
        verify(operatorPort, never()).createOperator(any());
    }

    @Test
    @DisplayName("772 S7: tenantId null → 422 (DTO 가 먼저 막지만 유스케이스도 '*' 만 통과시킨다)")
    void createOperator_nullTenant_invitationRequired() {
        assertThatThrownBy(() -> useCase.createOperator(
                "staff@example.com", "Staff", null, List.of(), actor(), "provisioning", null))
                .isInstanceOf(OperatorInvitationRequiredException.class);
        verifyNoInteractions(operatorPort, auditor);
    }

    // ── '*' create (대조군 — 772 전과 같다) ───────────────────────────────────────

    @Test
    @DisplayName("대조군: '*' 생성 성공 — 비밀번호 해시 · 역할 · 감사(target_tenant='*') · D2/D3 검사, 신원 링크 없음")
    void createOperator_platform_success_persists_hash_and_audits() {
        when(operatorPort.existsByTenantIdAndEmail(PLATFORM, "new@example.com")).thenReturn(false);
        Map<String, AdminOperatorPort.RoleView> resolved = new LinkedHashMap<>();
        resolved.put("SUPER_ADMIN", role(1L, "SUPER_ADMIN"));
        when(operatorPort.resolveRolesByName(List.of("SUPER_ADMIN"))).thenReturn(resolved);
        when(passwordHasher.hash("StrongPass1!")).thenReturn("hash-value");
        when(operatorPort.createOperator(any(AdminOperatorPort.NewOperator.class)))
                .thenReturn(createdView(42L, "new@example.com", "New Op"));
        when(operatorPort.resolveActorInternalId("actor-uuid")).thenReturn(99L);
        when(auditor.newAuditId()).thenReturn("audit-new");

        CreateOperatorUseCase.CreateOperatorResult result = useCase.createOperator(
                "new@example.com", "New Op", "StrongPass1!",
                List.of("SUPER_ADMIN"), actor(), "bootstrap", PLATFORM);

        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.status()).isEqualTo("ACTIVE");
        assertThat(result.roles()).containsExactly("SUPER_ADMIN");
        assertThat(result.totpEnrolled()).isFalse();
        assertThat(result.auditId()).isEqualTo("audit-new");
        assertThat(result.tenantId()).isEqualTo(PLATFORM);

        verify(passwordHasher, times(1)).hash("StrongPass1!");
        verify(operatorPort, times(1)).saveOperatorRoles(anyList());
        verify(tenantScopeGuard).requireTenantInScope(any(), any(), eq(PLATFORM), eq(ActionCode.OPERATOR_CREATE));
        verify(roleGrantGuard).requireGrantable(any(), any(), eq(ActionCode.OPERATOR_CREATE), eq(PLATFORM));
        // ADR-034 U4 identity link was always skipped for '*' — and '*' is now the only create.
        verify(operatorPort, never()).linkIdentity(anyLong(), anyString(), any(Instant.class));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> captor =
                ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).record(captor.capture());
        assertThat(captor.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_CREATE);
        assertThat(captor.getValue().targetType()).isEqualTo("OPERATOR");
        assertThat(captor.getValue().outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(captor.getValue().reason()).isEqualTo("bootstrap");
        assertThat(captor.getValue().targetTenantId()).isEqualTo(PLATFORM);
    }

    @Test
    @DisplayName("TASK-BE-249: 비-플랫폼 actor 의 '*' 생성 → 403 TENANT_SCOPE_DENIED + DENIED 감사, 미생성")
    void createOperator_non_platform_scope_actor_cannot_create_platform_scope_operator() {
        actorIsCompanyScoped();

        assertThatThrownBy(() -> useCase.createOperator(
                "super@example.com", "Super", "StrongPass1!",
                List.of("SUPER_ADMIN"), actor(), "reason", PLATFORM))
                .isInstanceOf(TenantScopeDeniedException.class)
                .hasMessageContaining("platform-scope");

        verify(auditor).recordCrossTenantDenied(any(), eq("fan-platform"), eq(ActionCode.OPERATOR_CREATE),
                anyString(), eq(PLATFORM));
        verify(operatorPort, never()).createOperator(any());
    }

    @Test
    @DisplayName("TASK-BE-262: 같은 '*' 에 같은 이메일 → OperatorEmailConflictException, INSERT · 감사 전")
    void createOperator_duplicate_email_throws_conflict_before_persist() {
        when(operatorPort.existsByTenantIdAndEmail(PLATFORM, "dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.createOperator(
                "dup@example.com", "Dup", "StrongPass1!", List.of(), actor(), "reason", PLATFORM))
                .isInstanceOf(OperatorEmailConflictException.class);

        verify(operatorPort, never()).createOperator(any());
        verify(auditor, never()).record(any());
    }

    @Test
    @DisplayName("알 수 없는 role 이름 → RoleNotFoundException, 미생성")
    void createOperator_unknown_role_throws_role_not_found() {
        when(operatorPort.existsByTenantIdAndEmail(anyString(), anyString())).thenReturn(false);
        when(operatorPort.resolveRolesByName(List.of("DOES_NOT_EXIST")))
                .thenThrow(new RoleNotFoundException("Unknown role name: DOES_NOT_EXIST"));

        assertThatThrownBy(() -> useCase.createOperator(
                "ok@example.com", "Op", "StrongPass1!",
                List.of("DOES_NOT_EXIST"), actor(), "reason", PLATFORM))
                .isInstanceOf(RoleNotFoundException.class);

        verify(operatorPort, never()).createOperator(any());
    }

    @Test
    @DisplayName("role 목록이 비어 있어도 '*' 생성은 허용된다")
    void createOperator_empty_roles_is_allowed() {
        stubHappyCreate("empty@example.com", 55L);

        CreateOperatorUseCase.CreateOperatorResult result = useCase.createOperator(
                "empty@example.com", "Empty", "StrongPass1!", List.of(), actor(), "reason", PLATFORM);

        assertThat(result.roles()).isEmpty();
        verify(operatorPort).saveOperatorRoles(anyList());
    }

    // ── TASK-BE-377 (ADR-MONO-035 O2 / step 4c): optional password = OIDC-only ──

    @Test
    @DisplayName("BE-377: password null → OIDC-only 운영자 (passwordHash=NULL, hasher 미호출)")
    void createOperator_nullPassword_createsOidcOnlyOperator() {
        stubHappyCreate("oidc@example.com", 80L);

        useCase.createOperator("oidc@example.com", "Oidc Only", null, List.of(), actor(), "provisioning", PLATFORM);

        verify(passwordHasher, never()).hash(anyString());
        ArgumentCaptor<AdminOperatorPort.NewOperator> captor =
                ArgumentCaptor.forClass(AdminOperatorPort.NewOperator.class);
        verify(operatorPort).createOperator(captor.capture());
        assertThat(captor.getValue().passwordHash()).isNull();
    }

    @Test
    @DisplayName("BE-377: password blank → OIDC-only 운영자 (passwordHash=NULL)")
    void createOperator_blankPassword_createsOidcOnlyOperator() {
        stubHappyCreate("blank@example.com", 81L);

        useCase.createOperator("blank@example.com", "Blank Pw", "   ", List.of(), actor(), "provisioning", PLATFORM);

        verify(passwordHasher, never()).hash(anyString());
        ArgumentCaptor<AdminOperatorPort.NewOperator> captor =
                ArgumentCaptor.forClass(AdminOperatorPort.NewOperator.class);
        verify(operatorPort).createOperator(captor.capture());
        assertThat(captor.getValue().passwordHash()).isNull();
    }

    @Test
    @DisplayName("BE-377: password 제공 → break-glass hash 저장")
    void createOperator_withPassword_hashesBreakGlass() {
        stubHappyCreate("bg@example.com", 82L);
        when(passwordHasher.hash("StrongPass1!")).thenReturn("bg-hash");

        useCase.createOperator("bg@example.com", "Break Glass", "StrongPass1!", List.of(), actor(), "provisioning",
                PLATFORM);

        verify(passwordHasher, times(1)).hash("StrongPass1!");
        ArgumentCaptor<AdminOperatorPort.NewOperator> captor =
                ArgumentCaptor.forClass(AdminOperatorPort.NewOperator.class);
        verify(operatorPort).createOperator(captor.capture());
        assertThat(captor.getValue().passwordHash()).isEqualTo("bg-hash");
    }
}
