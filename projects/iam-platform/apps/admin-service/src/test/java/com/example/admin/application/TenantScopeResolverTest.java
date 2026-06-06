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
 * TASK-BE-326 / ADR-MONO-020 D6 step 1 — unit tests for {@link TenantScopeResolver}.
 *
 * <p>AC-2: effective scope = assignment tenantIds ∪ {legacy tenantId};
 * {@code '*'} → {@code {"*"}}; no assignments → {legacy} (NET-ZERO).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TenantScopeResolver 단위 테스트 (TASK-BE-326 dual-read)")
class TenantScopeResolverTest {

    @Mock
    private OperatorTenantAssignmentPort assignmentPort;

    @InjectMocks
    private TenantScopeResolver resolver;

    @Test
    @DisplayName("NET-ZERO: 배정 0개 → {legacy home tenantId}")
    void noAssignments_returnsLegacyOnly() {
        when(assignmentPort.findAssignedTenantIds(1L)).thenReturn(Set.of());

        Set<String> result = resolver.resolveEffectiveTenantScope(1L, "fan-platform");

        assertThat(result).containsExactly("fan-platform");
    }

    @Test
    @DisplayName("배정 2개 → union(legacy ∪ assigned)")
    void twoAssignments_returnsUnion() {
        when(assignmentPort.findAssignedTenantIds(2L)).thenReturn(Set.of("wms", "scm"));

        Set<String> result = resolver.resolveEffectiveTenantScope(2L, "fan-platform");

        assertThat(result).containsExactlyInAnyOrder("fan-platform", "wms", "scm");
    }

    @Test
    @DisplayName("배정에 home tenant 가 중복 포함되어도 set 중복 제거")
    void assignmentIncludesHome_deduplicated() {
        when(assignmentPort.findAssignedTenantIds(3L)).thenReturn(Set.of("wms", "fan-platform"));

        Set<String> result = resolver.resolveEffectiveTenantScope(3L, "fan-platform");

        assertThat(result).containsExactlyInAnyOrder("fan-platform", "wms");
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
