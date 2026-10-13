package com.example.admin.application;

import com.example.admin.application.exception.OperatorAlreadyProvisionedException;
import com.example.admin.application.exception.OperatorOwnedTenantLimitExceededException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.infrastructure.persistence.rbac.CachingPermissionEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-474 (ADR-MONO-044 D1/D3) — orchestration + fail-closed compensation; TASK-MONO-784 S5
 * (onboarding-api.md § 다회사 운영자와 온보딩 · D-12 · OD-1) — a caller that already has an operator row.
 */
class SelfServiceOnboardingUseCaseTest {

    private static final String TENANT = "acme-corp";

    private final TenantProvisioningPort tenantPort = mock(TenantProvisioningPort.class);
    private final FirstAdminProvisioner provisioner = mock(FirstAdminProvisioner.class);
    private final AdminOperatorPort operatorPort = mock(AdminOperatorPort.class);
    private final CachingPermissionEvaluator cache = mock(CachingPermissionEvaluator.class);
    private final SelfServiceOnboardingUseCase useCase = new SelfServiceOnboardingUseCase(tenantPort, provisioner,
            new OperatorOidcSubjectResolver(operatorPort),
            new OperatorMembershipAddition(operatorPort, mock(com.example.admin.application.port.OperatorTenantAssignmentPort.class), cache),
            new OperatorOwnedTenantLimit(operatorPort, 2));

    @Test
    @DisplayName("D1: creates the tenant (B2B) then mints the first admin; no compensation on success")
    void happyPath() {
        when(provisioner.provision(eq(TENANT), any(), any(), any()))
                .thenReturn(new FirstAdminProvisioner.Result("op-uuid", 42L));

        SelfServiceOnboardingUseCase.Result result =
                useCase.onboard(TENANT, "Acme Corp", "acc-1", "owner@acme.com", "Owner");

        verify(tenantPort).create(eq(TENANT), eq("Acme Corp"), eq("B2B_ENTERPRISE"));
        verify(provisioner).provision(eq(TENANT), eq("acc-1"), eq("owner@acme.com"), eq("Owner"));
        // no compensation
        verify(tenantPort, never()).update(any(), any(), any());
        assertThat(result.tenantId()).isEqualTo(TENANT);
        assertThat(result.operatorId()).isEqualTo("op-uuid");
        assertThat(result.roles()).containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("D3: first-admin provisioning failure compensates the orphan tenant (SUSPEND) and rethrows")
    void compensatesOnProvisioningFailure() {
        RuntimeException boom = new IllegalStateException("mint failed");
        when(provisioner.provision(eq(TENANT), any(), any(), any())).thenThrow(boom);

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-1", "owner@acme.com", "Owner"))
                .isSameAs(boom);

        // tenant was created, then compensated by SUSPEND — no orphan ACTIVE tenant
        verify(tenantPort).create(eq(TENANT), any(), eq("B2B_ENTERPRISE"));
        verify(tenantPort).update(eq(TENANT), isNull(), eq("SUSPENDED"));
    }

    @Test
    @DisplayName("D3: a compensation failure does not mask the original provisioning error")
    void compensationFailureDoesNotMaskOriginal() {
        RuntimeException boom = new IllegalStateException("mint failed");
        when(provisioner.provision(eq(TENANT), any(), any(), any())).thenThrow(boom);
        when(tenantPort.update(eq(TENANT), isNull(), eq("SUSPENDED")))
                .thenThrow(new RuntimeException("account-service down"));

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-1", "owner@acme.com", "Owner"))
                .isSameAs(boom);
    }

    @Test
    @DisplayName("S1-11 대조군: 측면 없는 호출자는 그대로 온보딩된다(같은 질의가 비어 있음) — 표 1행")
    void noFacet_proceeds() {
        when(operatorPort.findByOidcSubject("acc-1")).thenReturn(Optional.empty());
        when(provisioner.provision(eq(TENANT), any(), any(), any()))
                .thenReturn(new FirstAdminProvisioner.Result("op-uuid", 42L));

        useCase.onboard(TENANT, "Acme Corp", "acc-1", "owner@acme.com", "Owner");

        verify(operatorPort).findByOidcSubject("acc-1");
        verify(tenantPort).create(eq(TENANT), eq("Acme Corp"), eq("B2B_ENTERPRISE"));
        verify(provisioner, never()).addMembership(any(), any());
    }

    /**
     * 🔴 TASK-MONO-784 S5 — the decision table of onboarding-api.md § 다회사 운영자와 온보딩, with REAL membership /
     * cap / provisioner code over real-writing in-memory ports (a mock would let a write into the wrong tenant
     * pass). The OIDC lookup is the only stub.
     */
    @Nested
    @DisplayName("🔴 784 S5 — 이미 운영자 측면이 있는 호출자 (판정 표 2~5행 · 테넌트 생성 전)")
    class ExistingFacet {

        private static final long OP = 41L;
        private static final String SUB = "acc-41";

        private final TenantProvisioningPort tenants = mock(TenantProvisioningPort.class);
        private final OperatorOidcSubjectResolver resolver = mock(OperatorOidcSubjectResolver.class);
        private final CachingPermissionEvaluator evaluatorCache = mock(CachingPermissionEvaluator.class);
        private final InMemoryRoleRowsPort operators = new InMemoryRoleRowsPort()
                .role(1, "TENANT_ADMIN").role(2, "TENANT_BILLING_ADMIN").role(3, "SUPPORT_LOCK")
                .role(9, "SUPER_ADMIN");
        private final InMemoryAssignmentPort assignments = new InMemoryAssignmentPort().allowCreate();
        private final OperatorMembershipAddition addition =
                new OperatorMembershipAddition(operators, assignments, evaluatorCache);

        private SelfServiceOnboardingUseCase useCase(int cap) {
            FirstAdminProvisioner real = new FirstAdminProvisioner(operators, assignments,
                    mock(AccountServiceClient.class), addition);
            return new SelfServiceOnboardingUseCase(tenants, real, resolver, addition,
                    new OperatorOwnedTenantLimit(operators, cap));
        }

        private AdminOperatorPort.OperatorView facet(String home, String status) {
            AdminOperatorPort.OperatorView v = new AdminOperatorPort.OperatorView(OP, "op-41", home,
                    "owner@acme.com", null, "Owner", status, null, null, Instant.EPOCH, Instant.EPOCH, null,
                    "identity-41");
            operators.operator(v);
            when(resolver.resolve(SUB)).thenReturn(Optional.of(v));
            return v;
        }

        @Test
        @DisplayName("🔴 AC-3 (표 5행): ACTIVE 회사 측면 · 상한 미만 → 201 · operatorId = 기존 행 · 새 테넌트 TENANT_ADMIN + BILLING 행 + 배정 · 기존 회사 행 바이트 불변 · 운영자 행 쓰기 0 · 캐시 무효화")
        void activeCompanyFacet_belowCap_addsMembershipToThatRow() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null)
                    .row(OP, "SUPPORT_LOCK", "other-co", 5L, null);
            assignments.row(OP, "home-co", null, null).row(OP, "other-co", 5L, List.of("d1"));
            List<InMemoryRoleRowsPort.Row> rolesBefore = new ArrayList<>(operators.rows);
            List<InMemoryAssignmentPort.Row> assignmentsBefore = new ArrayList<>(assignments.rows);

            SelfServiceOnboardingUseCase.Result result =
                    useCase(2).onboard(TENANT, "Acme Corp", SUB, "owner@acme.com", "Owner");

            assertThat(result.operatorId()).isEqualTo("op-41");
            assertThat(result.roles()).containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
            verify(tenants).create(TENANT, "Acme Corp", "B2B_ENTERPRISE");
            verify(tenants, never()).update(any(), any(), any());
            assertThat(operators.rowsIn(OP, TENANT)).extracting(InMemoryRoleRowsPort.Row::roleName)
                    .containsExactlyInAnyOrder("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
            assertThat(operators.rowsIn(OP, TENANT)).allSatisfy(r -> {
                assertThat(r.groupOrigin()).isNull();
                assertThat(r.orgNodeId()).isNull();
            });
            // 🔴 the existing companies' rows — the first N rows, untouched (granted_at included)
            assertThat(operators.rows.subList(0, rolesBefore.size())).containsExactlyElementsOf(rolesBefore);
            assertThat(operators.rowsOutside(OP, TENANT)).containsExactlyElementsOf(rolesBefore);
            assertThat(assignments.rows).containsAll(assignmentsBefore).hasSize(assignmentsBefore.size() + 1);
            assertThat(assignments.find(OP, TENANT)).isPresent();
            verify(evaluatorCache).invalidate("op-41");
        }

        @Test
        @DisplayName("🔴 (표 4행) OD-1 상한 도달(TENANT_ADMIN 테넌트 2) → 422 · 테넌트 생성 0 · 쓰기 0")
        void atCap_422_beforeTenantCreation() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null)
                    .row(OP, "TENANT_ADMIN", "second-co", null, null);
            int rowsBefore = operators.rows.size();

            assertThatThrownBy(() -> useCase(2).onboard(TENANT, "Acme Corp", SUB, "owner@acme.com", "Owner"))
                    .isInstanceOf(OperatorOwnedTenantLimitExceededException.class);

            verifyNoInteractions(tenants, evaluatorCache);
            assertThat(operators.rows).hasSize(rowsBefore);
            assertThat(assignments.rows).isEmpty();
        }

        @Test
        @DisplayName("대조군: 같은 두 회사 소유라도 상한 3 이면 통과(값은 설정 — 773 AC-0)")
        void cap3_twoOwned_passes() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null)
                    .row(OP, "TENANT_ADMIN", "second-co", null, null);

            useCase(3).onboard(TENANT, "Acme Corp", SUB, "owner@acme.com", "Owner");

            verify(tenants).create(TENANT, "Acme Corp", "B2B_ENTERPRISE");
            assertThat(operators.rowsIn(OP, TENANT)).hasSize(2);
        }

        @Test
        @DisplayName("🔴 (표 2행) '*' 측면 → 409 OPERATOR_ALREADY_PROVISIONED · 테넌트 생성 0")
        void platformFacet_409() {
            facet("*", "ACTIVE");
            operators.row(OP, "SUPER_ADMIN", "*", null, null);

            assertThatThrownBy(() -> useCase(2).onboard(TENANT, "Acme Corp", SUB, "owner@acme.com", "Owner"))
                    .isInstanceOf(OperatorAlreadyProvisionedException.class)
                    .satisfies(e -> {
                        OperatorAlreadyProvisionedException ex = (OperatorAlreadyProvisionedException) e;
                        assertThat(ex.getCode()).isEqualTo("OPERATOR_ALREADY_PROVISIONED");
                        assertThat(ex.getHttpStatus()).isEqualTo(409);
                    });
            verifyNoInteractions(tenants);
        }

        @Test
        @DisplayName("(표 3행) 비-ACTIVE 측면(SUSPENDED) → 409 · 테넌트 생성 0 · 상한은 묻지 않는다")
        void suspendedFacet_409() {
            facet("home-co", "SUSPENDED");

            assertThatThrownBy(() -> useCase(2).onboard(TENANT, "Acme Corp", SUB, "owner@acme.com", "Owner"))
                    .isInstanceOf(OperatorAlreadyProvisionedException.class);
            verifyNoInteractions(tenants);
        }

        @Test
        @DisplayName("소속 추가가 테넌트 생성 뒤 실패 → SUSPEND 보상 · 원래 예외 · 캐시 무효화 0")
        void additionFailure_compensates() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null);
            // the admin-side transaction fails after the tenant exists (e.g. a racing write of the same row)
            FirstAdminProvisioner failing = mock(FirstAdminProvisioner.class);
            RuntimeException boom = new IllegalStateException("duplicate PK (operator, tenant)");
            when(failing.addMembership(any(), eq(TENANT))).thenThrow(boom);
            SelfServiceOnboardingUseCase uc = new SelfServiceOnboardingUseCase(tenants, failing, resolver, addition,
                    new OperatorOwnedTenantLimit(operators, 2));

            assertThatThrownBy(() -> uc.onboard(TENANT, "Acme Corp", SUB, "owner@acme.com", "Owner")).isSameAs(boom);

            verify(tenants).create(TENANT, "Acme Corp", "B2B_ENTERPRISE");
            verify(tenants).update(TENANT, null, "SUSPENDED");
            verifyNoInteractions(evaluatorCache);
        }
    }
}
