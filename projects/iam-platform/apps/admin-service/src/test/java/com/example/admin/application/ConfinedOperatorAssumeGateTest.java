package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.domain.rbac.AdminOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-751 — {@code admin_operators.confined_tenant_id} at the assume gate. Owner decision
 * 2026-10-03 «데모 운영자는 팬 전용으로»: the demo platform operator ({@code '*'}) may assume
 * {@code fan-platform} ONLY. Without the confinement a {@code '*'} operator passes step 2
 * («assigned to any tenant») for every registered tenant.
 *
 * <p>Controls on the same use case: a normal {@code '*'} operator (confinement NULL) is still
 * assigned everywhere, and the demo customer operator ({@code demo-corp}) is unchanged — still
 * assigned to its own tenants and still refused {@code fan-platform} (R3).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("admin-service — confined_tenant_id 로 묶인 운영자는 그 테넌트만 assume 한다 (TASK-MONO-751)")
class ConfinedOperatorAssumeGateTest {

    private static final String FAN = AdminOperator.FAN_PLATFORM_TENANT_ID;
    private static final String SUB = "0199de70-0000-7000-8000-00000000ad06";

    @Mock private AdminOperatorPort operatorPort;
    @Mock private OperatorTenantAssignmentPort assignmentPort;
    @Mock private TenantScopeResolver tenantScopeResolver;
    @Mock private TenantPartnershipPort partnershipPort;

    private static AdminOperatorPort.OperatorView operator(String home, String confinedTenantId) {
        return new AdminOperatorPort.OperatorView(
                6L, "demo-platform", home, "platform@demo.com", "hash", "Op", "ACTIVE",
                null, null, Instant.now(), Instant.now(), null, null, confinedTenantId);
    }

    private OperatorAssignmentCheckUseCase gate() {
        return new OperatorAssignmentCheckUseCase(
                tenantScopeResolver, assignmentPort, new OperatorOidcSubjectResolver(operatorPort),
                partnershipPort, new UnboundedHostEntitledScopeResolver(),
                // TASK-MONO-771 S4: no tenant has an entry policy in this suite (row absent = off).
                new OperatorSecondFactorRequirement(operatorPort, assignmentPort, ids -> java.util.Set.of()));
    }

    @Test
    @DisplayName("데모 플랫폼 운영자('*', confined=fan-platform) → fan-platform assigned=true")
    void demoPlatformOperator_fanPlatform_isAssigned() {
        when(operatorPort.findByOidcSubject(SUB))
                .thenReturn(Optional.of(operator(AdminOperator.PLATFORM_TENANT_ID, FAN)));

        assertThat(gate().check(SUB, FAN).assigned()).isTrue();
    }

    @ParameterizedTest(name = "🔴 데모 플랫폼 운영자 → {0} not-assigned")
    @ValueSource(strings = {"ecommerce", "wms", "scm", "erp", "finance", "demo-corp", "iam", "acme-corp"})
    void demoPlatformOperator_everyOtherTenant_isRefused(String tenant) {
        when(operatorPort.findByOidcSubject(SUB))
                .thenReturn(Optional.of(operator(AdminOperator.PLATFORM_TENANT_ID, FAN)));

        assertThat(gate().check(SUB, tenant).assigned())
                .as("«데모 운영자는 팬 전용으로» — a '*' operator confined to fan-platform must not "
                        + "assume %s", tenant)
                .isFalse();
        // The refusal is the confinement, not a later step: nothing below step 1b was asked.
        verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
        verify(partnershipPort, never()).findActivePartnership(anyString(), anyString());
    }

    @ParameterizedTest(name = "대조군: 일반 플랫폼 운영자(confined=NULL) → {0} assigned=true (불변)")
    @ValueSource(strings = {"fan-platform", "ecommerce", "wms", "erp", "demo-corp"})
    void normalPlatformOperator_unchanged(String tenant) {
        when(operatorPort.findByOidcSubject(SUB))
                .thenReturn(Optional.of(operator(AdminOperator.PLATFORM_TENANT_ID, null)));

        assertThat(gate().check(SUB, tenant).assigned()).isTrue();
    }

    @Test
    @DisplayName("대조군: demo@demo.com 모양(demo-corp 고객사 운영자, confined=NULL) → ecommerce 는 그대로 assigned")
    void demoCustomerOperator_ownTenant_unchanged() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("demo-corp", null)));
        when(tenantScopeResolver.resolveEffectiveTenantScope(6L, "demo-corp"))
                .thenReturn(Set.of("demo-corp", "ecommerce"));
        when(assignmentPort.findOrgScope(6L, "ecommerce")).thenReturn(null);

        assertThat(gate().check(SUB, "ecommerce").assigned()).isTrue();
    }

    @Test
    @DisplayName("대조군: demo@demo.com 모양 → fan-platform 은 여전히 not-assigned (R3 불변)")
    void demoCustomerOperator_fanPlatform_stillRefused() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("demo-corp", null)));

        assertThat(gate().check(SUB, FAN).assigned()).isFalse();
    }

    @Test
    @DisplayName("좁히기만 한다: 고객사 운영자를 fan-platform 에 묶어도 열리지 않는다 (R3 가 이긴다)")
    void confinementNeverOpens_customerConfinedToFan_stillRefused() {
        when(operatorPort.findByOidcSubject(SUB)).thenReturn(Optional.of(operator("demo-corp", FAN)));
        lenient().when(tenantScopeResolver.resolveEffectiveTenantScope(6L, "demo-corp"))
                .thenReturn(Set.of("demo-corp", FAN));

        assertThat(gate().check(SUB, FAN).assigned()).isFalse();
    }

    @Test
    @DisplayName("isConfinedAway — NULL·공백은 제한 없음, 같은 테넌트는 통과, 다른 테넌트는 거절")
    void isConfinedAway_predicate() {
        assertThat(OperatorAssignmentCheckUseCase.isConfinedAway(null, "wms")).isFalse();
        assertThat(OperatorAssignmentCheckUseCase.isConfinedAway("  ", "wms")).isFalse();
        assertThat(OperatorAssignmentCheckUseCase.isConfinedAway(FAN, FAN)).isFalse();
        assertThat(OperatorAssignmentCheckUseCase.isConfinedAway(FAN, "wms")).isTrue();
    }
}
