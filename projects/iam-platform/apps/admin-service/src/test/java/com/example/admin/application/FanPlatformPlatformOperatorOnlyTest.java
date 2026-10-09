package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantDomainSubscriptionPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.application.tenant.SubscriptionMutationSummary;
import com.example.admin.domain.rbac.AdminOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static com.example.admin.application.OperatorUseCaseTestSupport.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-750 (ADR-MONO-079 ACCEPTED — A, D4-A, rider R3) — the IAM half of the platform
 * operator's fan path: {@code fan-platform} is assumable by a <b>platform</b> operator only.
 *
 * <p>Why this needs a gate at all: account-service V0031 subscribes {@code fan-platform} to the
 * {@code fan} domain, so ANY token minted by assuming that tenant is derived {@code FAN_OPERATOR}
 * (auth-service {@code OperatorRoleDerivation}), and fan artist-service admits that role on its
 * directory-management paths by plain {@code tenant_id} equality. Whoever can assume
 * {@code fan-platform} therefore manages the fan directory. R3 says that is the platform
 * operator alone.
 *
 * <p>Each refusal below is paired with a control on the <em>same</em> use case, so a refusal that
 * came from something other than the rule under test (a mock left unstubbed, a scope guard that
 * throws for everyone) would show up as a failing control rather than as a passing refusal.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("admin-service — fan-platform 은 플랫폼 운영자만 assume 한다 (TASK-MONO-750 · ADR-079 R3)")
class FanPlatformPlatformOperatorOnlyTest {

    private static final String FAN = AdminOperator.FAN_PLATFORM_TENANT_ID;
    private static final String OIDC_SUBJECT = "00000000-0000-7000-8000-0000000000f7";

    @Mock private AdminOperatorPort operatorPort;
    @Mock private OperatorTenantAssignmentPort assignmentPort;
    @Mock private TenantScopeGuard tenantScopeGuard;
    @Mock private AdminActionAuditor auditor;
    @Mock private TenantScopeResolver tenantScopeResolver;
    @Mock private TenantPartnershipPort partnershipPort;
    @Mock private TenantDomainSubscriptionPort subscriptionPort;

    private static AdminOperatorPort.OperatorView operator(String homeTenantId) {
        return new AdminOperatorPort.OperatorView(
                7L, OIDC_SUBJECT, homeTenantId, "op@example.com", "hash",
                "Op", "ACTIVE", null, null, Instant.now(), Instant.now(), null, null);
    }

    private OperatorAssignmentCheckUseCase checkUseCase() {
        return new OperatorAssignmentCheckUseCase(
                tenantScopeResolver, assignmentPort, new OperatorOidcSubjectResolver(operatorPort),
                partnershipPort, new UnboundedHostEntitledScopeResolver(),
                // TASK-MONO-771 S4: no tenant has an entry policy in this suite (row absent = off).
                new OperatorSecondFactorRequirement(operatorPort, assignmentPort, ids -> java.util.Set.of()));
    }

    @Nested
    @DisplayName("assume 게이트 (GET /internal/operator-assignments/check)")
    class AssumeGate {

        @Test
        @DisplayName("플랫폼 운영자('*') → fan-platform assigned=true — D4-A 가 연 길")
        void platformOperator_isAssigned() {
            when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                    .thenReturn(Optional.of(operator(AdminOperator.PLATFORM_TENANT_ID)));

            OperatorAssignmentCheckUseCase.Result result = checkUseCase().check(OIDC_SUBJECT, FAN);

            assertThat(result.assigned()).isTrue();
            assertThat(result.delegatedScope()).isNull();
        }

        @Test
        @DisplayName("🔴 고객사 운영자 → 배정 행이 있어도 fan-platform 은 not-assigned (R3)")
        void customerOperator_withRow_isRefused() {
            when(operatorPort.findByOidcSubject(OIDC_SUBJECT)).thenReturn(Optional.of(operator("demo-corp")));
            // The inputs that would otherwise say yes: an effective scope that CONTAINS fan-platform
            // (an assignment row, however it got there) and a row-level org scope behind it.
            lenient().when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "demo-corp"))
                    .thenReturn(Set.of("demo-corp", FAN, "ecommerce"));
            lenient().when(assignmentPort.findOrgScope(anyLong(), anyString())).thenReturn(null);

            assertThat(checkUseCase().check(OIDC_SUBJECT, FAN).assigned())
                    .as("a customer operator must never assume fan-platform — that token would carry "
                            + "FAN_OPERATOR and manage the fan directory")
                    .isFalse();
        }

        @Test
        @DisplayName("대조군: 같은 고객사 운영자·같은 스코프로 ecommerce 는 assigned=true")
        void customerOperator_sameScope_otherB2cTenant_isAssigned() {
            // ecommerce is ALSO a B2C_CONSUMER tenant and the demo operator is assigned to it
            // (TASK-BE-576). The refusal above must be about fan-platform, not about B2C tenants or
            // about this operator.
            when(operatorPort.findByOidcSubject(OIDC_SUBJECT)).thenReturn(Optional.of(operator("demo-corp")));
            when(tenantScopeResolver.resolveEffectiveTenantScope(7L, "demo-corp"))
                    .thenReturn(Set.of("demo-corp", FAN, "ecommerce"));
            when(assignmentPort.findOrgScope(7L, "ecommerce")).thenReturn(null);

            assertThat(checkUseCase().check(OIDC_SUBJECT, "ecommerce").assigned()).isTrue();
        }

        @Test
        @DisplayName("고객사 운영자 · 파트너십 분기도 닿지 않는다 — 판정이 그 앞에서 내려진다")
        void customerOperator_partnershipBranchNeverConsulted() {
            when(operatorPort.findByOidcSubject(OIDC_SUBJECT)).thenReturn(Optional.of(operator("acme-corp")));

            assertThat(checkUseCase().check(OIDC_SUBJECT, FAN).assigned()).isFalse();
            verify(partnershipPort, never()).findActivePartnership(anyString(), anyString());
            verify(tenantScopeResolver, never()).resolveEffectiveTenantScope(anyLong(), anyString());
        }
    }

    @Nested
    @DisplayName("배정 생성 (POST /api/admin/operators/{id}/assignments/{tenantId})")
    class AssignmentCreation {

        @Test
        @DisplayName("🔴 fan-platform → 403 TENANT_SCOPE_DENIED, 행·감사 없음 (SUPER_ADMIN 이라도)")
        void assignToFanPlatform_refused() {
            ManageOperatorAssignmentUseCase useCase =
                    new ManageOperatorAssignmentUseCase(operatorPort, assignmentPort, tenantScopeGuard, auditor);

            assertThatThrownBy(() -> useCase.assignOperator("op-1", FAN, actor(), "reason"))
                    .isInstanceOf(TenantScopeDeniedException.class)
                    .hasMessageContaining(FAN);

            verify(assignmentPort, never()).createAssignment(anyLong(), anyString(), any());
            verify(auditor, never()).recordWithPermission(any(), any());
        }

        @Test
        @DisplayName("대조군: 같은 경로로 ecommerce 는 배정된다")
        void assignToEcommerce_created() {
            ManageOperatorAssignmentUseCase useCase =
                    new ManageOperatorAssignmentUseCase(operatorPort, assignmentPort, tenantScopeGuard, auditor);
            when(operatorPort.findByOperatorId("op-1")).thenReturn(Optional.of(operator("demo-corp")));
            when(assignmentPort.assignmentExists(7L, "ecommerce")).thenReturn(false);

            assertThat(useCase.assignOperator("op-1", "ecommerce", actor(), "reason").tenantId())
                    .isEqualTo("ecommerce");
            verify(assignmentPort).createAssignment(anyLong(), eq("ecommerce"), any());
        }
    }

    @Nested
    @DisplayName("fan 도메인 구독 (POST /api/admin/subscriptions)")
    class FanSubscription {

        @Test
        @DisplayName("🔴 고객사 테넌트가 fan / fan-platform 구독 → TENANT_SCOPE_DENIED, account-service 호출 없음")
        void customerTenant_fanDomain_refused() {
            ManageSubscriptionUseCase useCase =
                    new ManageSubscriptionUseCase(subscriptionPort, auditor, tenantScopeGuard);

            assertThatThrownBy(() -> useCase.subscribe("demo-corp", "fan", actor(), "reason"))
                    .isInstanceOf(TenantScopeDeniedException.class)
                    .hasMessageContaining(FAN);
            assertThatThrownBy(() -> useCase.subscribe("demo-corp", "fan-platform", actor(), "reason"))
                    .isInstanceOf(TenantScopeDeniedException.class);

            verify(subscriptionPort, never()).subscribe(anyString(), anyString(), any(), any());
        }

        @Test
        @DisplayName("fan-platform 의 fan 구독은 통과한다 — 스코프 게이트로 넘어간다(플랫폼 운영자만 가진 스코프)")
        void fanPlatform_fanDomain_reachesTheScopeGate() {
            ManageSubscriptionUseCase useCase =
                    new ManageSubscriptionUseCase(subscriptionPort, auditor, tenantScopeGuard);
            SubscriptionMutationSummary summary = new SubscriptionMutationSummary(
                    FAN, "fan", null, "ACTIVE", Instant.parse("2026-10-03T00:00:00Z"));
            when(subscriptionPort.subscribe(eq(FAN), eq("fan"), any(), any())).thenReturn(summary);
            when(auditor.newAuditId()).thenReturn("audit-1");

            assertThat(useCase.subscribe(FAN, "fan", actor(), "reason")).isSameAs(summary);
            verify(tenantScopeGuard).requireTenantInScope(any(), any(), eq(FAN), any());
        }

        @Test
        @DisplayName("대조군: 고객사 테넌트의 다른 도메인(ecommerce) 구독은 지금처럼 통과한다")
        void customerTenant_otherDomain_unchanged() {
            ManageSubscriptionUseCase useCase =
                    new ManageSubscriptionUseCase(subscriptionPort, auditor, tenantScopeGuard);
            SubscriptionMutationSummary summary = new SubscriptionMutationSummary(
                    "demo-corp", "ecommerce", null, "ACTIVE", Instant.parse("2026-10-03T00:00:00Z"));
            when(subscriptionPort.subscribe(eq("demo-corp"), eq("ecommerce"), any(), any())).thenReturn(summary);
            when(auditor.newAuditId()).thenReturn("audit-2");

            assertThat(useCase.subscribe("demo-corp", "ecommerce", actor(), "reason")).isSameAs(summary);
        }
    }
}
