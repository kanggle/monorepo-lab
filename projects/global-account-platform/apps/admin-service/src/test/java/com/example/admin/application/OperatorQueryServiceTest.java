package com.example.admin.application;

import com.example.admin.application.exception.OperatorUnauthorizedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.AdminOperatorTotpPort;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperatorQueryServiceTest {

    @Mock AdminOperatorPort operatorPort;
    @Mock AdminOperatorTotpPort totpPort;

    @InjectMocks OperatorQueryService service;

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

        OperatorQueryService.OperatorPage result = service.listOperators(null, 0, 20);

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

        OperatorQueryService.OperatorPage result = service.listOperators("SUSPENDED", 0, 20);

        assertThat(result.content()).isEmpty();
        verify(operatorPort, never()).findOperatorsPage(eq(null), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    @DisplayName("TOTP enrolledAt 이 있는 operator 는 totpEnrolled=true")
    void getCurrentOperator_totp_enrolled_at_present() {
        Instant enrolled = Instant.parse("2026-05-01T00:00:00Z");
        AdminOperatorPort.OperatorView op = new AdminOperatorPort.OperatorView(
                10L, "op-uuid", "fan-platform", "op@example.com", "hash", "Display", "ACTIVE",
                enrolled, null, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
        when(operatorPort.findByOperatorId("op-uuid")).thenReturn(Optional.of(op));
        when(operatorPort.findRolesForOperator(10L)).thenReturn(List.of());

        OperatorQueryService.OperatorSummary result = service.getCurrentOperator("op-uuid");

        assertThat(result.totpEnrolled()).isTrue();
    }
}
