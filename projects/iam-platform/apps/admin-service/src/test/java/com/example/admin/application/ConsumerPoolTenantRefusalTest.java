package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantDomainSubscriptionPort;
import com.example.admin.application.port.TenantPartnershipPort;
import com.example.admin.domain.rbac.AdminOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static com.example.admin.application.OperatorUseCaseTestSupport.actor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-614 — admin-service surfaces that could make the reserved {@code consumer-pool} tenant an
 * operator / assume target refuse it (defense in depth; auth-service refuses to mint it regardless).
 * Pre-existing gap these close: assignment creation never validated that the tenant exists, and
 * account-service V0029 made the pool a real ACTIVE tenant row.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("admin-service — consumer-pool 은 운영자 대상 테넌트가 될 수 없다 (TASK-BE-614)")
class ConsumerPoolTenantRefusalTest {

    private static final String POOL = AdminOperator.CONSUMER_POOL_TENANT_ID;
    private static final String OIDC_SUBJECT = "00000000-0000-7000-8000-0000000000a1";

    @Mock private AdminOperatorPort operatorPort;
    @Mock private OperatorTenantAssignmentPort assignmentPort;
    @Mock private TenantScopeGuard tenantScopeGuard;
    @Mock private AdminActionAuditor auditor;
    @Mock private TenantScopeResolver tenantScopeResolver;
    @Mock private TenantPartnershipPort partnershipPort;
    @Mock private TenantDomainSubscriptionPort subscriptionPort;

    private static AdminOperatorPort.OperatorView operator(String tenantId) {
        return new AdminOperatorPort.OperatorView(
                7L, OIDC_SUBJECT, tenantId, "op@example.com", "hash",
                "Op", "ACTIVE", null, null, Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("배정 생성: consumer-pool → 403 TENANT_SCOPE_DENIED 형태, 행·감사 없음 (SUPER_ADMIN 이라도)")
    void assignOperator_toPool_refused() {
        ManageOperatorAssignmentUseCase useCase =
                new ManageOperatorAssignmentUseCase(operatorPort, assignmentPort, tenantScopeGuard, auditor);

        assertThatThrownBy(() -> useCase.assignOperator("op-1", POOL, actor(), "reason"))
                .isInstanceOf(TenantScopeDeniedException.class)
                .hasMessageContaining(POOL);

        verify(assignmentPort, never()).createAssignment(anyLong(), anyString(), any());
        verify(auditor, never()).recordWithPermission(any(), any());
    }

    @Test
    @DisplayName("대조군: 같은 경로로 일반 테넌트는 배정된다")
    void assignOperator_toOrdinaryTenant_created() {
        ManageOperatorAssignmentUseCase useCase =
                new ManageOperatorAssignmentUseCase(operatorPort, assignmentPort, tenantScopeGuard, auditor);
        when(operatorPort.findByOperatorId("op-1")).thenReturn(Optional.of(operator("acme-corp")));
        when(assignmentPort.assignmentExists(7L, "globex")).thenReturn(false);

        assertThat(useCase.assignOperator("op-1", "globex", actor(), "reason").tenantId()).isEqualTo("globex");
        verify(assignmentPort).createAssignment(anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("assume 게이트: platform-scope 운영자 · 배정 행이 있어도 consumer-pool 은 not-assigned")
    void assignmentCheck_pool_notAssigned_evenForPlatformScopeWithRow() {
        OperatorAssignmentCheckUseCase useCase = new OperatorAssignmentCheckUseCase(
                tenantScopeResolver, assignmentPort, new OperatorOidcSubjectResolver(operatorPort),
                partnershipPort, new UnboundedHostEntitledScopeResolver(),
                // TASK-MONO-771 S4: no tenant has an entry policy in this suite (row absent = off).
                new OperatorSecondFactorRequirement(operatorPort, assignmentPort, ids -> java.util.Set.of()));
        // the "row exists" / "platform scope" inputs that would otherwise say yes
        lenient().when(operatorPort.findByOidcSubject(OIDC_SUBJECT))
                .thenReturn(Optional.of(operator(AdminOperator.PLATFORM_TENANT_ID)));
        lenient().when(assignmentPort.assignmentExists(anyLong(), anyString())).thenReturn(true);

        assertThat(useCase.check(OIDC_SUBJECT, POOL).assigned()).isFalse();
        assertThat(useCase.check(OIDC_SUBJECT, "acme-corp").assigned())
                .as("control: the same platform-scope operator is assigned to an ordinary tenant")
                .isTrue();
    }

    @Test
    @DisplayName("도메인 구독: consumer-pool → TENANT_SCOPE_DENIED, account-service 호출 없음")
    void subscribe_pool_refused() {
        ManageSubscriptionUseCase useCase = new ManageSubscriptionUseCase(subscriptionPort, auditor, tenantScopeGuard);

        assertThatThrownBy(() -> useCase.subscribe(POOL, "ecommerce", actor(), "reason"))
                .isInstanceOf(TenantScopeDeniedException.class);

        verify(subscriptionPort, never()).subscribe(anyString(), anyString(), any(), any());
    }
}
