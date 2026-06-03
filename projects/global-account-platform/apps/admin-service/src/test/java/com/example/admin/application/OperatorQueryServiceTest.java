package com.example.admin.application;

import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.AdminOperatorTotpPort;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.admin.application.OperatorUseCaseTestSupport.operator;
import static com.example.admin.application.OperatorUseCaseTestSupport.role;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperatorQueryServiceTest {

    @Mock AdminOperatorPort operatorPort;
    @Mock AdminOperatorTotpPort totpPort;
    @Mock TenantScopeResolver tenantScopeResolver;

    @InjectMocks OperatorQueryService service;

    /**
     * TASK-MONO-175 — default caller for the list tests: a platform-scope
     * ({@code '*'}) operator listing {@code '*'} → routes to the unscoped
     * {@code findOperatorsPage} (the path the legacy list tests mock). LENIENT
     * strictness leaves this unused for the getCurrentOperator tests.
     */
    @BeforeEach
    void stubPlatformCaller() {
        AdminOperatorPort.OperatorView caller =
                operator(99L, "super", "super@ex.com", "ACTIVE", "*", null);
        when(operatorPort.findByOperatorId("super")).thenReturn(Optional.of(caller));
        when(tenantScopeResolver.resolveEffectiveTenantScope(eq(99L), eq("*")))
                .thenReturn(Set.of("*"));
    }

    @Test
    @DisplayName("현재 운영자 조회 시 role 목록이 포함된 summary 를 반환한다")
    void getCurrentOperator_returns_summary_with_roles() {
        AdminOperatorPort.OperatorView op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(operatorPort.findRolesForOperator(10L))
                .thenReturn(List.of(role(1L, "SUPER_ADMIN")));
        when(totpPort.findByOperator(10L)).thenReturn(Optional.empty());

        OperatorQueryService.OperatorSummary result = service.getCurrentOperator("op-uuid");

        assertThat(result.operatorId()).isEqualTo("op-uuid");
        assertThat(result.email()).isEqualTo("op@example.com");
        assertThat(result.roles()).containsExactly("SUPER_ADMIN");
        assertThat(result.totpEnrolled()).isFalse();
    }

    @Test
    @DisplayName("운영자 레지스트리에 없는 토큰으로 조회하면 OperatorUnauthorizedException 을 던진다")
    void getCurrentOperator_missing_operator_throws_unauthorized() {
        when(operatorPort.findByOperatorId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentOperator("missing"))
                .isInstanceOf(OperatorUnauthorizedException.class);
    }

    @Test
    @DisplayName("운영자 목록 조회 시 페이지마다 role 매핑이 포함된다")
    void listOperators_returns_paginated_roles() {
        AdminOperatorPort.OperatorView op = operator(1L, "op-1-uuid", "one@ex.com", "ACTIVE");
        AdminOperatorPort.OperatorPage page = new AdminOperatorPort.OperatorPage(
                List.of(op), 1L, 0, 20, 1);
        when(operatorPort.findOperatorsPage(null, 0, 20)).thenReturn(page);
        when(operatorPort.bulkLoadRoleNamesByOperator(anyCollection()))
                .thenReturn(Map.of(1L, List.of("SUPPORT_LOCK")));
        when(totpPort.findEnrolledOperatorIds(anyCollection())).thenReturn(Set.of());

        OperatorQueryService.OperatorPage result = service.listOperators(null, 0, 20, "super", "*");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).roles()).containsExactly("SUPPORT_LOCK");
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("status 필터가 주어지면 status 가 그대로 port 로 전달된다")
    void listOperators_status_filter_routes_to_status_query() {
        AdminOperatorPort.OperatorPage empty = new AdminOperatorPort.OperatorPage(
                List.of(), 0L, 0, 20, 0);
        when(operatorPort.findOperatorsPage(eq("SUSPENDED"), eq(0), eq(20))).thenReturn(empty);
        when(operatorPort.bulkLoadRoleNamesByOperator(anyCollection())).thenReturn(Map.of());
        when(totpPort.findEnrolledOperatorIds(anyCollection())).thenReturn(Set.of());

        OperatorQueryService.OperatorPage result = service.listOperators("SUSPENDED", 0, 20, "super", "*");

        assertThat(result.content()).isEmpty();
        verify(operatorPort, never()).findOperatorsPage(eq(null), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("TASK-BE-308: listOperators 는 operator 별 financeDefaultAccountId 를 summary 에 carrying 한다")
    void listOperators_propagates_financeDefaultAccountId_per_operator() {
        AdminOperatorPort.OperatorView opWithValue =
                operator(1L, "op-1-uuid", "one@ex.com", "ACTIVE", "fan-platform", "acc-uuid-7");
        AdminOperatorPort.OperatorView opNoValue =
                operator(2L, "op-2-uuid", "two@ex.com", "ACTIVE", "fan-platform", null);
        AdminOperatorPort.OperatorPage page = new AdminOperatorPort.OperatorPage(
                List.of(opWithValue, opNoValue), 2L, 0, 20, 1);
        when(operatorPort.findOperatorsPage(null, 0, 20)).thenReturn(page);
        when(operatorPort.bulkLoadRoleNamesByOperator(anyCollection()))
                .thenReturn(Map.of(1L, List.of("SUPER_ADMIN"), 2L, List.of("SUPPORT_LOCK")));
        when(totpPort.findEnrolledOperatorIds(anyCollection())).thenReturn(Set.of());

        OperatorQueryService.OperatorPage result = service.listOperators(null, 0, 20, "super", "*");

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).financeDefaultAccountId()).isEqualTo("acc-uuid-7");
        assertThat(result.content().get(1).financeDefaultAccountId()).isNull();
    }

    @Test
    @DisplayName("TASK-MONO-175: 활성 테넌트로 스코핑 — home==X OR assignment(X) operator 만 (audit 미러)")
    void listOperators_scopes_to_active_tenant_home_or_assignment() {
        // caller = multi-operator (home=acme, effective={acme,globex}); requested globex.
        AdminOperatorPort.OperatorView caller =
                operator(5L, "multi", "multi@ex.com", "ACTIVE", "acme-corp", null);
        when(operatorPort.findByOperatorId("multi")).thenReturn(Optional.of(caller));
        when(tenantScopeResolver.resolveEffectiveTenantScope(eq(5L), eq("acme-corp")))
                .thenReturn(Set.of("acme-corp", "globex-corp"));
        AdminOperatorPort.OperatorView opG =
                operator(7L, "g-op", "g@ex.com", "ACTIVE", "globex-corp", null);
        when(operatorPort.findOperatorsPageByTenant(eq("globex-corp"), eq(null), eq(0), eq(20)))
                .thenReturn(new AdminOperatorPort.OperatorPage(List.of(opG), 1L, 0, 20, 1));
        when(operatorPort.bulkLoadRoleNamesByOperator(anyCollection()))
                .thenReturn(Map.of(7L, List.of("SUPPORT_LOCK")));
        when(totpPort.findEnrolledOperatorIds(anyCollection())).thenReturn(Set.of());

        OperatorQueryService.OperatorPage result =
                service.listOperators(null, 0, 20, "multi", "globex-corp");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).operatorId()).isEqualTo("g-op");
        // scoped path used, NOT the unscoped cross-tenant findOperatorsPage.
        verify(operatorPort, never()).findOperatorsPage(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("TASK-MONO-175: effective scope 밖 테넌트 요청은 TenantScopeDenied (403)")
    void listOperators_rejects_tenant_outside_effective_scope() {
        // caller home=acme, no globex assignment → effective={acme}; requests globex.
        AdminOperatorPort.OperatorView caller =
                operator(5L, "acme-op", "a@ex.com", "ACTIVE", "acme-corp", null);
        when(operatorPort.findByOperatorId("acme-op")).thenReturn(Optional.of(caller));
        when(tenantScopeResolver.resolveEffectiveTenantScope(eq(5L), eq("acme-corp")))
                .thenReturn(Set.of("acme-corp"));

        assertThatThrownBy(() -> service.listOperators(null, 0, 20, "acme-op", "globex-corp"))
                .isInstanceOf(TenantScopeDeniedException.class);
        verify(operatorPort, never()).findOperatorsPageByTenant(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("TOTP enrolledAt 이 있는 operator 는 totpEnrolled=true")
    void getCurrentOperator_totp_enrolled_at_present() {
        Instant enrolled = Instant.parse("2026-05-01T00:00:00Z");
        AdminOperatorPort.OperatorView op = new AdminOperatorPort.OperatorView(
                10L, "op-uuid", "fan-platform", "op@example.com", "hash", "Display", "ACTIVE",
                enrolled, null, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"), null);
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(operatorPort.findRolesForOperator(10L)).thenReturn(List.of());

        OperatorQueryService.OperatorSummary result = service.getCurrentOperator("op-uuid");

        assertThat(result.totpEnrolled()).isTrue();
    }
}
