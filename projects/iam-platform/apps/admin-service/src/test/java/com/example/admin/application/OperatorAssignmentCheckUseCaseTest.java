package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-327 / ADR-MONO-020 § 3.3 step 2 (D2) — unit tests for
 * {@link OperatorAssignmentCheckUseCase}, the read-only authorization helper
 * behind {@code GET /internal/operator-assignments/check}.
 *
 * <p>Covers the fail-closed resolution order (AC-4): unknown subject /
 * non-ACTIVE operator → {@code false}; {@code '*'} platform → {@code true};
 * otherwise the {@link TenantScopeResolver} effective scope decides.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OperatorAssignmentCheckUseCase 단위 테스트 (TASK-BE-327 assignment gate)")
class OperatorAssignmentCheckUseCaseTest {

    private static final String OIDC_SUBJECT = "00000000-0000-7000-8000-0000000000a1";

    @Mock
    private AdminOperatorPort operatorPort;

    @Mock
    private TenantScopeResolver tenantScopeResolver;

    @Mock
    private OperatorTenantAssignmentPort assignmentPort;

    @InjectMocks
    private OperatorAssignmentCheckUseCase useCase;

    private AdminOperatorPort.OperatorView operator(String tenantId, String status, long internalId) {
        return new AdminOperatorPort.OperatorView(
                internalId, OIDC_SUBJECT, tenantId, "op@example.com", "hash",
                "Op", status, null, null, Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("assigned: effective scope 가 선택 tenant 포함 → true")
    void assigned_whenScopeContainsTenant() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp", "globex"));

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "globex")).isTrue();
    }

    @Test
    @DisplayName("not-assigned: effective scope 에 선택 tenant 없음 → false")
    void notAssigned_whenScopeMissingTenant() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp"));

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "globex")).isFalse();
    }

    @Test
    @DisplayName("unknown subject (no operator row) → false, scope 미조회")
    void unknownSubject_failClosed() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT)).thenReturn(Optional.empty());

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "acme-corp")).isFalse();
        verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
    }

    @Test
    @DisplayName("non-ACTIVE operator (DISABLED) → false, scope 미조회")
    void nonActiveOperator_failClosed() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "DISABLED", 7L)));

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "acme-corp")).isFalse();
        verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
    }

    @Test
    @DisplayName("'*' platform-scope operator → 임의의 비-blank tenant 에 대해 true, scope 미조회")
    void platformScope_assignedToAnyTenant() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("*", "ACTIVE", 99L)));

        assertThat(useCase.isAssigned(OIDC_SUBJECT, "acme-corp")).isTrue();
        verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
    }

    @Test
    @DisplayName("blank tenantId → false, operator 미조회")
    void blankTenant_failClosed() {
        assertThat(useCase.isAssigned(OIDC_SUBJECT, "  ")).isFalse();
        verify(operatorPort, never()).findByOidcSubject(any());
    }

    // ── TASK-BE-338: org_scope on the check result ──────────────────────────────

    @Test
    @DisplayName("BE-338: assigned + 설정된 org_scope → Result.orgScope=그 값")
    void assigned_withPopulatedOrgScope() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp", "globex"));
        when(assignmentPort.findOrgScope(7L, "globex")).thenReturn(List.of("dept-sales"));

        OperatorAssignmentCheckUseCase.Result result = useCase.check(OIDC_SUBJECT, "globex");

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).containsExactly("dept-sales");
    }

    @Test
    @DisplayName("BE-338: assigned + org_scope 미설정(NULL) → Result.orgScope=null (net-zero → '*')")
    void assigned_withNullOrgScope_netZero() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp"));
        when(assignmentPort.findOrgScope(7L, "acme-corp")).thenReturn(null);

        OperatorAssignmentCheckUseCase.Result result = useCase.check(OIDC_SUBJECT, "acme-corp");

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).isNull();
    }

    @Test
    @DisplayName("BE-338: '*' platform-scope → Result.orgScope=null (no explicit row), findOrgScope 미조회")
    void platformScope_orgScopeNull() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("*", "ACTIVE", 99L)));

        OperatorAssignmentCheckUseCase.Result result = useCase.check(OIDC_SUBJECT, "acme-corp");

        assertThat(result.assigned()).isTrue();
        assertThat(result.orgScope()).isNull();
        verify(assignmentPort, never()).findOrgScope(anyLong(), anyString());
    }

    @Test
    @DisplayName("BE-338: not-assigned → Result.orgScope=null, findOrgScope 미조회")
    void notAssigned_orgScopeNull() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp"));

        OperatorAssignmentCheckUseCase.Result result = useCase.check(OIDC_SUBJECT, "globex");

        assertThat(result.assigned()).isFalse();
        assertThat(result.orgScope()).isNull();
        verify(assignmentPort, never()).findOrgScope(anyLong(), anyString());
    }

    // ── TASK-MONO-295 (ADR-MONO-040 Phase 2): DUAL-KEY operator resolution ───────

    private static final String OPERATOR_EMAIL = "acme-operator@example.com";

    @Test
    @DisplayName("AC-0: account_id 키 hit → 기존 동작 그대로 (email fallback 미조회)")
    void dualKey_accountIdHit_emailFallbackNotConsulted() {
        // The target end-state: oidc_subject already backfilled to account_id.
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp"));

        OperatorAssignmentCheckUseCase.Result result =
                useCase.check(OIDC_SUBJECT, OPERATOR_EMAIL, "acme-corp");

        assertThat(result.assigned()).isTrue();
        // account_id hit → the email fallback lookup is never performed.
        verify(operatorPort, never()).findByOidcSubject(OPERATOR_EMAIL);
    }

    @Test
    @DisplayName("AC-0: account_id 키 miss → 레거시 email fallback 으로 resolve (기존 operator 무회귀)")
    void dualKey_accountIdMiss_resolvesViaEmailFallback() {
        // The live transition state: sub is now the account UUID, but
        // admin_operators.oidc_subject still holds the seed EMAIL.
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT)).thenReturn(Optional.empty());
        when(operatorPort.findByOidcSubject(OPERATOR_EMAIL))
                .thenReturn(Optional.of(operator("acme-corp", "ACTIVE", 7L)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "acme-corp"))
                .thenReturn(Set.of("acme-corp"));

        OperatorAssignmentCheckUseCase.Result result =
                useCase.check(OIDC_SUBJECT, OPERATOR_EMAIL, "acme-corp");

        // The operator still resolves — AC-0 no-regression proven.
        assertThat(result.assigned()).isTrue();
    }

    @Test
    @DisplayName("AC-0: 두 키 모두 miss → fail-closed false (fallback 이 게이트를 완화하지 않음)")
    void dualKey_bothKeysMiss_failClosed() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT)).thenReturn(Optional.empty());
        when(operatorPort.findByOidcSubject(OPERATOR_EMAIL)).thenReturn(Optional.empty());

        OperatorAssignmentCheckUseCase.Result result =
                useCase.check(OIDC_SUBJECT, OPERATOR_EMAIL, "acme-corp");

        assertThat(result.assigned()).isFalse();
        verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
    }

    @Test
    @DisplayName("dual-key: subjectEmail null + account_id miss → fallback 시도 없이 fail-closed")
    void dualKey_nullEmail_noFallbackAttempt() {
        when(operatorPort.findByOidcSubject(OIDC_SUBJECT)).thenReturn(Optional.empty());

        OperatorAssignmentCheckUseCase.Result result =
                useCase.check(OIDC_SUBJECT, null, "acme-corp");

        assertThat(result.assigned()).isFalse();
        // Only the account_id lookup was attempted (null email is skipped).
        verify(operatorPort).findByOidcSubject(OIDC_SUBJECT);
    }
}
