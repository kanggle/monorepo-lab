package com.example.admin.application;

import com.example.admin.application.port.OperatorTenantAssignmentPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TenantScopeResolver} — effective scope = the operator's memberships.
 *
 * <p>TASK-BE-326 introduced it as the dual-read {@code assignments ∪ {home}}. TASK-MONO-784 S2
 * (ADR-MONO-082 b · R1 · D3 · decision D-2) drops the home: a concrete home tenant is NOT in scope
 * unless an assignment row names it. Each «home is gone» case carries its control in the same test
 * (an assignment row is still in scope), so a resolver that returned nothing at all cannot pass.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TenantScopeResolver 단위 테스트 (TASK-MONO-784 S2 — 소속 = 배정 행만)")
class TenantScopeResolverTest {

    @Mock
    private OperatorTenantAssignmentPort assignmentPort;

    @InjectMocks
    private TenantScopeResolver resolver;

    @Test
    @DisplayName("🔴 784 S2: 홈만 있고 배정 0 → 빈 집합 (홈은 판정에서 읽지 않는다 — R1)")
    void homeOnly_noAssignment_isEmpty() {
        when(assignmentPort.findAssignedTenantIds(1L)).thenReturn(Set.of());

        Set<String> result = resolver.resolveEffectiveTenantScope(1L, "acme");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("🔴 784 S2: 홈 ∉ 배정 → 홈은 빠지고 배정만 (대조군: 배정 테넌트는 그대로 범위 안)")
    void homeNotAssigned_excluded_assignmentStillIn() {
        when(assignmentPort.findAssignedTenantIds(2L)).thenReturn(Set.of("wms", "scm"));

        Set<String> result = resolver.resolveEffectiveTenantScope(2L, "acme");

        assertThat(result)
                .as("control — the assignment rows are the scope")
                .containsExactlyInAnyOrder("wms", "scm");
        assertThat(result)
                .as("the home tenant without an assignment row is out of scope (784 AC-0 F4)")
                .doesNotContain("acme");
    }

    @Test
    @DisplayName("V0051 백필 뒤 단일 회사 운영자: 홈 배정 행이 있으면 지금과 같은 {홈}")
    void homeAssigned_singleCompany_unchanged() {
        when(assignmentPort.findAssignedTenantIds(3L)).thenReturn(Set.of("acme"));

        Set<String> result = resolver.resolveEffectiveTenantScope(3L, "acme");

        assertThat(result).containsExactly("acme");
    }

    @Test
    @DisplayName("'*' platform operator → {\"*\"}, assignment 미조회 (sentinel 무변경)")
    void platformScope_returnsStarSentinel_noAssignmentRead() {
        Set<String> result = resolver.resolveEffectiveTenantScope(99L, "*");

        assertThat(result).containsExactly("*");
        // assignments are NOT expanded for the platform sentinel.
        verify(assignmentPort, never()).findAssignedTenantIds(org.mockito.ArgumentMatchers.anyLong());
    }
}
