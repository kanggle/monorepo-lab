package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.OperatorLookupPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-357 — the shared read-surface tenant gate. These cases were lifted from
 * the inline gate that previously lived in {@code AuditQueryUseCase} (TASK-BE-249/
 * BE-262/BE-326) so that audit + account search are gated by ONE tested unit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QueryTenantScopeGate 단위 테스트")
class QueryTenantScopeGateTest {

    @Mock
    private OperatorLookupPort operatorLookupPort;
    @Mock
    private TenantScopeResolver tenantScopeResolver;
    @Mock
    private AdminActionAuditor auditor;

    private QueryTenantScopeGate gate;

    @BeforeEach
    void setUp() {
        gate = new QueryTenantScopeGate(operatorLookupPort, tenantScopeResolver, auditor);

        // Default: normal operator in "fan-platform" (non-platform-scope), no assignments.
        when(operatorLookupPort.findByOperatorId("op-1"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(1L, "op-1", "fan-platform")));
        when(tenantScopeResolver.resolveEffectiveTenantScope(any(), eq("fan-platform")))
                .thenReturn(Set.of("fan-platform"));
        when(tenantScopeResolver.resolveEffectiveTenantScope(any(), eq("*")))
                .thenReturn(Set.of("*"));
    }

    private static OperatorContext op(String operatorId) {
        return new OperatorContext(operatorId, "jti-1");
    }

    @Test
    @DisplayName("requestedTenantId 생략 → 운영자 자신의 테넌트로 default, 비-플랫폼")
    void resolve_nullRequested_defaultsToOwnTenant() {
        QueryTenantScopeGate.Resolved r = gate.resolve(op("op-1"), null, ActionCode.ACCOUNT_SEARCH, "account.read");

        assertThat(r.tenantId()).isEqualTo("fan-platform");
        assertThat(r.isPlatformScope()).isFalse();
        verify(auditor, never()).recordCrossTenantDenied(any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("자기 테넌트 명시 → 허용")
    void resolve_ownTenant_allowed() {
        QueryTenantScopeGate.Resolved r = gate.resolve(op("op-1"), "fan-platform", ActionCode.ACCOUNT_SEARCH, "account.read");

        assertThat(r.tenantId()).isEqualTo("fan-platform");
        assertThat(r.isPlatformScope()).isFalse();
    }

    @Test
    @DisplayName("scope 밖 테넌트 요청 → TenantScopeDeniedException + DENIED 감사행")
    void resolve_outOfScope_deniedAndAudited() {
        assertThatThrownBy(() -> gate.resolve(op("op-1"), "ecommerce", ActionCode.ACCOUNT_SEARCH, "account.read"))
                .isInstanceOf(TenantScopeDeniedException.class);

        verify(auditor).recordCrossTenantDenied(
                any(), eq("fan-platform"), eq(ActionCode.ACCOUNT_SEARCH), eq("account.read"), eq("ecommerce"));
    }

    @Test
    @DisplayName("TASK-BE-326: 배정(assignment)된 비-home 테넌트 → 허용, deny 없음")
    void resolve_assignedNonHomeTenant_allowed() {
        when(tenantScopeResolver.resolveEffectiveTenantScope(any(), eq("fan-platform")))
                .thenReturn(Set.of("fan-platform", "ecommerce"));

        QueryTenantScopeGate.Resolved r = gate.resolve(op("op-1"), "ecommerce", ActionCode.ACCOUNT_SEARCH, "account.read");

        assertThat(r.tenantId()).isEqualTo("ecommerce");
        assertThat(r.isPlatformScope()).isFalse();
        verify(auditor, never()).recordCrossTenantDenied(any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("SUPER_ADMIN(tenant='*')이 특정 테넌트 요청 → 허용, isPlatformScope=true")
    void resolve_superAdmin_specificTenant_allowedPlatformScope() {
        when(operatorLookupPort.findByOperatorId("super-op"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(99L, "super-op", "*")));

        QueryTenantScopeGate.Resolved r = gate.resolve(op("super-op"), "ecommerce", ActionCode.ACCOUNT_SEARCH, "account.read");

        assertThat(r.tenantId()).isEqualTo("ecommerce");
        assertThat(r.isPlatformScope()).isTrue();
        verify(auditor, never()).recordCrossTenantDenied(any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("SUPER_ADMIN(tenant='*')이 tenantId 생략 → '*'로 default, isPlatformScope=true")
    void resolve_superAdmin_nullRequested_defaultsToStar() {
        when(operatorLookupPort.findByOperatorId("super-op"))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(99L, "super-op", "*")));

        QueryTenantScopeGate.Resolved r = gate.resolve(op("super-op"), null, ActionCode.ACCOUNT_SEARCH, "account.read");

        assertThat(r.tenantId()).isEqualTo("*");
        assertThat(r.isPlatformScope()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 operator → TenantScopeDeniedException")
    void resolve_unknownOperator_throws() {
        when(operatorLookupPort.findByOperatorId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gate.resolve(op("ghost"), null, ActionCode.ACCOUNT_SEARCH, "account.read"))
                .isInstanceOf(TenantScopeDeniedException.class)
                .hasMessageContaining("Operator not found");
    }
}
