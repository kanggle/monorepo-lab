package com.example.admin.application;

import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.AdminOperatorTotpPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.Permission;
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
    @Mock OperatorTenantAssignmentPort assignmentPort;
    @Mock QueryTenantScopeGate queryTenantScopeGate;

    @InjectMocks OperatorQueryService service;

    /** Default list caller: platform grant listing '*' → the unscoped cross-tenant finder. */
    @BeforeEach
    void stubPlatformCaller() {
        when(queryTenantScopeGate.resolveWithoutDenyRow(
                eq(new OperatorContext("super", null)), eq("*"), eq(Permission.OPERATOR_MANAGE)))
                .thenReturn(new QueryTenantScopeGate.Resolved("*", true));
    }

    @Test
    @DisplayName("현재 운영자 조회 시 role 목록이 포함된 summary 를 반환한다 (헤더 없음 → 모든 역할 행)")
    void getCurrentOperator_returns_summary_with_roles() {
        AdminOperatorPort.OperatorView op = operator(10L, "op-uuid", "op@example.com", "ACTIVE");
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(operatorPort.findRoleNamesByTenant(10L)).thenReturn(Map.of("fan-platform", List.of("SUPER_ADMIN")));
        when(totpPort.findByOperator(10L)).thenReturn(Optional.empty());

        OperatorQueryService.OperatorSummary result = service.getCurrentOperator("op-uuid");

        assertThat(result.operatorId()).isEqualTo("op-uuid");
        assertThat(result.email()).isEqualTo("op@example.com");
        assertThat(result.roles()).containsExactly("SUPER_ADMIN");
        assertThat(result.totpEnrolled()).isFalse();
    }

    @Test
    @DisplayName("784 S3 /me: X-Tenant-Id=beta → roles = beta 의 역할 + '*' 행 · memberships = 소속마다 그 회사 역할 (오름차순)")
    void getCurrentOperator_actingTenant_and_memberships() {
        AdminOperatorPort.OperatorView op = operator(10L, "op-uuid", "op@example.com", "ACTIVE", "alpha", null);
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(operatorPort.findRoleNamesByTenant(10L)).thenReturn(Map.of(
                "alpha", List.of("TENANT_ADMIN"),
                "beta", List.of("SUPPORT_READONLY")));
        when(assignmentPort.findAssignedTenantIds(10L)).thenReturn(Set.of("gamma", "beta", "alpha"));

        OperatorQueryService.OperatorSummary inBeta = service.getCurrentOperator("op-uuid", "beta");
        assertThat(inBeta.roles())
                .as("A's TENANT_ADMIN must not open the operator menu while acting in B")
                .containsExactly("SUPPORT_READONLY");
        assertThat(inBeta.memberships()).containsExactly(
                new OperatorQueryService.Membership("alpha", List.of("TENANT_ADMIN")),
                new OperatorQueryService.Membership("beta", List.of("SUPPORT_READONLY")),
                new OperatorQueryService.Membership("gamma", List.of()));

        // control: no header → every role (unchanged)
        assertThat(service.getCurrentOperator("op-uuid", null).roles())
                .containsExactly("SUPPORT_READONLY", "TENANT_ADMIN");
    }

    @Test
    @DisplayName("784 S3 /me: '*' 운영자 → memberships = [] (소속을 읽지 않는다) · 헤더가 있어도 '*' 역할은 보인다")
    void getCurrentOperator_platform_noMemberships() {
        AdminOperatorPort.OperatorView op = operator(11L, "plat", "p@example.com", "ACTIVE", "*", null);
        when(operatorPort.findByOperatorId("plat")).thenReturn(Optional.of(op));
        when(operatorPort.findRoleNamesByTenant(11L)).thenReturn(Map.of("*", List.of("SUPER_ADMIN")));

        OperatorQueryService.OperatorSummary r = service.getCurrentOperator("plat", "acme");

        assertThat(r.roles()).containsExactly("SUPER_ADMIN");
        assertThat(r.memberships()).isEmpty();
        verify(assignmentPort, never()).findAssignedTenantIds(any());
    }

    @Test
    @DisplayName("운영자 레지스트리에 없는 토큰으로 조회하면 OperatorUnauthorizedException 을 던진다")
    void getCurrentOperator_missing_operator_throws_unauthorized() {
        when(operatorPort.findByOperatorId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrentOperator("missing"))
                .isInstanceOf(OperatorUnauthorizedException.class);
    }

    @Test
    @DisplayName("운영자 목록 조회 시 페이지마다 role 매핑이 포함된다 ('*' 목록 = 모든 역할 행)")
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
        assertThat(result.content().get(0).memberships()).as("never on a list item").isNull();
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
        verify(operatorPort, never()).findOperatorsPage(eq(null), anyInt(), anyInt());
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
    @DisplayName("784 S3: 회사 목록은 그 회사의 역할만 싣는다 (bulkLoad…InTenant) · 스코프 경로")
    void listOperators_scoped_rolesOfThatTenantOnly() {
        when(queryTenantScopeGate.resolveWithoutDenyRow(
                eq(new OperatorContext("multi", null)), eq("globex-corp"), eq(Permission.OPERATOR_MANAGE)))
                .thenReturn(new QueryTenantScopeGate.Resolved("globex-corp", false));
        AdminOperatorPort.OperatorView opG =
                operator(7L, "g-op", "g@ex.com", "ACTIVE", "globex-corp", null);
        when(operatorPort.findOperatorsPageByTenant(eq("globex-corp"), eq(null), eq(0), eq(20)))
                .thenReturn(new AdminOperatorPort.OperatorPage(List.of(opG), 1L, 0, 20, 1));
        when(operatorPort.bulkLoadRoleNamesByOperatorInTenant(anyCollection(), eq("globex-corp")))
                .thenReturn(Map.of(7L, List.of("SUPPORT_LOCK")));
        when(totpPort.findEnrolledOperatorIds(anyCollection())).thenReturn(Set.of());

        OperatorQueryService.OperatorPage result =
                service.listOperators(null, 0, 20, "multi", "globex-corp");

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).roles()).containsExactly("SUPPORT_LOCK");
        verify(operatorPort, never()).findOperatorsPage(any(), anyInt(), anyInt());
        verify(operatorPort, never()).bulkLoadRoleNamesByOperator(anyCollection());
    }

    @Test
    @DisplayName("TASK-BE-626: 목록 항목의 homeTenantId = 그 운영자 자신의 HOME 테넌트 (요청 테넌트가 아니다)")
    void listOperators_carries_each_operators_own_home_tenant() {
        when(queryTenantScopeGate.resolveWithoutDenyRow(
                eq(new OperatorContext("multi", null)), eq("globex-corp"), eq(Permission.OPERATOR_MANAGE)))
                .thenReturn(new QueryTenantScopeGate.Resolved("globex-corp", false));
        AdminOperatorPort.OperatorView homeOp =
                operator(7L, "g-home", "g@ex.com", "ACTIVE", "globex-corp", null);
        AdminOperatorPort.OperatorView assignedOp =
                operator(8L, "a-assigned", "a@ex.com", "ACTIVE", "acme-corp", null);
        when(operatorPort.findOperatorsPageByTenant(eq("globex-corp"), eq(null), eq(0), eq(20)))
                .thenReturn(new AdminOperatorPort.OperatorPage(List.of(homeOp, assignedOp), 2L, 0, 20, 1));
        when(operatorPort.bulkLoadRoleNamesByOperatorInTenant(anyCollection(), eq("globex-corp")))
                .thenReturn(Map.of());
        when(totpPort.findEnrolledOperatorIds(anyCollection())).thenReturn(Set.of());

        OperatorQueryService.OperatorPage result =
                service.listOperators(null, 0, 20, "multi", "globex-corp");

        assertThat(result.content()).extracting(OperatorQueryService.OperatorSummary::homeTenantId)
                .containsExactly("globex-corp", "acme-corp");
    }

    @Test
    @DisplayName("784 S3: 게이트가 거절하면 (grant scope 밖) TenantScopeDenied — 조회 없음")
    void listOperators_rejects_tenant_outside_grant_scope() {
        when(queryTenantScopeGate.resolveWithoutDenyRow(
                eq(new OperatorContext("acme-op", null)), eq("globex-corp"), eq(Permission.OPERATOR_MANAGE)))
                .thenThrow(new TenantScopeDeniedException("out"));

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
                Instant.parse("2026-01-01T00:00:00Z"), null, null);
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(operatorPort.findRoleNamesByTenant(10L)).thenReturn(Map.of());

        OperatorQueryService.OperatorSummary result = service.getCurrentOperator("op-uuid");

        assertThat(result.totpEnrolled()).isTrue();
    }
}
