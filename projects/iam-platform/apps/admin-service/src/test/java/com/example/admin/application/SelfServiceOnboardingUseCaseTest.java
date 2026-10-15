package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.OnboardingAccountNotEligibleException;
import com.example.admin.application.exception.OnboardingEmailNotVerifiedException;
import com.example.admin.application.exception.OperatorAlreadyProvisionedException;
import com.example.admin.application.exception.OperatorOwnedTenantLimitExceededException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.OperatorRowLockPort;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.application.port.VerifiedEmailMatchPort;
import com.example.admin.domain.rbac.DomainGrantSource;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-474 (ADR-MONO-044 D1/D3) — orchestration + fail-closed compensation; TASK-MONO-784 S5
 * (onboarding-api.md § 다회사 운영자와 온보딩 · D-12 · OD-1) — a caller that already has an operator row;
 * TASK-MONO-773 S3 — the trust gate (verified email · option A «no account record ∧ a facet» exemption) and the
 * creator's ORIGIN set row / cap re-count under the row lock (through the real provisioner).
 */
class SelfServiceOnboardingUseCaseTest {

    private static final String TENANT = "acme-corp";
    private static final Optional<OnboardingCallerAccount> OWNER =
            Optional.of(new OnboardingCallerAccount("owner@acme.com", "Owner"));

    private final TenantProvisioningPort tenantPort = mock(TenantProvisioningPort.class);
    private final FirstAdminProvisioner provisioner = mock(FirstAdminProvisioner.class);
    private final AdminOperatorPort operatorPort = mock(AdminOperatorPort.class);
    private final CachingPermissionEvaluator cache = mock(CachingPermissionEvaluator.class);
    private final VerifiedEmailMatchPort matchPort = mock(VerifiedEmailMatchPort.class);
    private final SelfServiceOnboardingUseCase useCase = new SelfServiceOnboardingUseCase(tenantPort, provisioner,
            new OperatorOidcSubjectResolver(operatorPort),
            new OperatorMembershipAddition(operatorPort, mock(com.example.admin.application.port.OperatorTenantAssignmentPort.class), cache),
            new OperatorOwnedTenantLimit(operatorPort, 3),
            new OnboardingTrustGate(matchPort));

    private void verified(String accountId) {
        when(matchPort.match(eq(accountId), anyString()))
                .thenReturn(new VerifiedEmailMatchPort.MatchResult(VerifiedEmailMatchPort.Outcome.MATCHED, Instant.EPOCH));
    }

    private void verdict(String accountId, VerifiedEmailMatchPort.Outcome outcome) {
        when(matchPort.match(eq(accountId), anyString()))
                .thenReturn(new VerifiedEmailMatchPort.MatchResult(outcome, null));
    }

    @Test
    @DisplayName("D1: creates the tenant (B2B) then mints the first admin; no compensation on success")
    void happyPath() {
        verified("acc-1");
        when(provisioner.provision(eq(TENANT), any(), any(), any()))
                .thenReturn(new FirstAdminProvisioner.Result("op-uuid", 42L));

        SelfServiceOnboardingUseCase.Result result = useCase.onboard(TENANT, "Acme Corp", "acc-1", OWNER);

        verify(tenantPort).create(eq(TENANT), eq("Acme Corp"), eq("B2B_ENTERPRISE"));
        verify(provisioner).provision(eq(TENANT), eq("acc-1"), eq("owner@acme.com"), eq("Owner"));
        verify(matchPort).match("acc-1", "owner@acme.com");
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
        verified("acc-1");
        RuntimeException boom = new IllegalStateException("mint failed");
        when(provisioner.provision(eq(TENANT), any(), any(), any())).thenThrow(boom);

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-1", OWNER)).isSameAs(boom);

        // tenant was created, then compensated by SUSPEND — no orphan ACTIVE tenant
        verify(tenantPort).create(eq(TENANT), any(), eq("B2B_ENTERPRISE"));
        verify(tenantPort).update(eq(TENANT), isNull(), eq("SUSPENDED"));
    }

    @Test
    @DisplayName("D3: a compensation failure does not mask the original provisioning error")
    void compensationFailureDoesNotMaskOriginal() {
        verified("acc-1");
        RuntimeException boom = new IllegalStateException("mint failed");
        when(provisioner.provision(eq(TENANT), any(), any(), any())).thenThrow(boom);
        when(tenantPort.update(eq(TENANT), isNull(), eq("SUSPENDED")))
                .thenThrow(new RuntimeException("account-service down"));

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-1", OWNER)).isSameAs(boom);
    }

    @Test
    @DisplayName("S1-11 대조군: 측면 없는 호출자는 그대로 온보딩된다(같은 질의가 비어 있음) — 표 1행")
    void noFacet_proceeds() {
        verified("acc-1");
        when(operatorPort.findByOidcSubject("acc-1")).thenReturn(Optional.empty());
        when(provisioner.provision(eq(TENANT), any(), any(), any()))
                .thenReturn(new FirstAdminProvisioner.Result("op-uuid", 42L));

        useCase.onboard(TENANT, "Acme Corp", "acc-1", OWNER);

        verify(operatorPort).findByOidcSubject("acc-1");
        verify(tenantPort).create(eq(TENANT), eq("Acme Corp"), eq("B2B_ENTERPRISE"));
        verify(provisioner, never()).addMembership(any(), any());
    }

    // ── 🔴 TASK-MONO-773 S3 — the trust gate, before anything is created ──

    @Test
    @DisplayName("🔴 773 게이트: 개인 계정 · 이메일 미인증 → EMAIL_NOT_VERIFIED · 테넌트 생성 0 · 프로비저너 0")
    void unverifiedEmail_refusedBeforeCreation() {
        verdict("acc-1", VerifiedEmailMatchPort.Outcome.NOT_VERIFIED);

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-1", OWNER))
                .isInstanceOf(OnboardingEmailNotVerifiedException.class);
        verifyNoInteractions(tenantPort, provisioner);
    }

    @Test
    @DisplayName("🔴 773 게이트: 계정 행이 있지만 개인 계정이 아님(NOT_ELIGIBLE) · 이메일 경합(MISMATCH) → ONBOARDING_ACCOUNT_NOT_ELIGIBLE(«개인 계정» 안내) · 생성 0")
    void notAPersonalAccount_refusedBeforeCreation() {
        verdict("acc-site", VerifiedEmailMatchPort.Outcome.NOT_ELIGIBLE);
        verdict("acc-race", VerifiedEmailMatchPort.Outcome.EMAIL_MISMATCH);

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-site", OWNER))
                .isInstanceOf(OnboardingAccountNotEligibleException.class)
                .hasMessageContaining("개인 계정");
        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-race", OWNER))
                .isInstanceOf(OnboardingAccountNotEligibleException.class);
        verifyNoInteractions(tenantPort, provisioner);
    }

    @Test
    @DisplayName("🔴 773 게이트 fail-closed: 판정을 못 받음 → 하위 예외 그대로(503) · 생성 0")
    void noVerdict_propagates() {
        DownstreamFailureException down = new DownstreamFailureException("account-service unavailable");
        when(matchPort.match(eq("acc-1"), anyString())).thenThrow(down);

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-1", OWNER)).isSameAs(down);
        verifyNoInteractions(tenantPort, provisioner);
    }

    @Test
    @DisplayName("🔴 773 option A: 계정 행 없음 ∧ 측면 없음 → ONBOARDING_ACCOUNT_NOT_ELIGIBLE · 매치 호출 0 · 생성 0")
    void noAccountRecordAndNoFacet_refused() {
        when(operatorPort.findByOidcSubject("acc-ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-ghost", Optional.empty()))
                .isInstanceOf(OnboardingAccountNotEligibleException.class);
        verifyNoInteractions(matchPort, tenantPort, provisioner);
    }

    /**
     * 🔴 TASK-MONO-784 S5 + 773 S3 — the decision table of onboarding-api.md § 다회사 운영자와 온보딩, with REAL
     * membership / cap / provisioner code over real-writing in-memory ports (a mock would let a write into the wrong
     * tenant pass). The OIDC lookup, the email verdict, the set port and the row lock are the only stubs.
     */
    @Nested
    @DisplayName("🔴 784 S5 · 773 S3 — 이미 운영자 측면이 있는 호출자 (판정 표 2~5행 · 테넌트 생성 전)")
    class ExistingFacet {

        private static final long OP = 41L;
        private static final String SUB = "acc-41";
        private static final long ALL_DOMAINS_FULL_ID = 14L;

        private final TenantProvisioningPort tenants = mock(TenantProvisioningPort.class);
        private final OperatorOidcSubjectResolver resolver = mock(OperatorOidcSubjectResolver.class);
        private final CachingPermissionEvaluator evaluatorCache = mock(CachingPermissionEvaluator.class);
        private final VerifiedEmailMatchPort match = mock(VerifiedEmailMatchPort.class);
        private final DomainPermissionSetPort setPort = mock(DomainPermissionSetPort.class);
        private final OperatorRowLockPort lockPort = mock(OperatorRowLockPort.class);
        private final InMemoryRoleRowsPort operators = new InMemoryRoleRowsPort()
                .role(1, "TENANT_ADMIN").role(2, "TENANT_BILLING_ADMIN").role(3, "SUPPORT_LOCK")
                .role(9, "SUPER_ADMIN");
        private final InMemoryAssignmentPort assignments = new InMemoryAssignmentPort().allowCreate();
        private final OperatorMembershipAddition addition =
                new OperatorMembershipAddition(operators, assignments, evaluatorCache);

        ExistingFacet() {
            when(setPort.findPresets()).thenReturn(List.of(new DomainPermissionSetPort.SetView(ALL_DOMAINS_FULL_ID,
                    "preset-all", null, "all-domains-full", "all", null, List.of(), Instant.EPOCH, Instant.EPOCH)));
        }

        private SelfServiceOnboardingUseCase useCase(int cap) {
            OperatorOwnedTenantLimit limit = new OperatorOwnedTenantLimit(operators, cap);
            FirstAdminProvisioner real = new FirstAdminProvisioner(operators, assignments,
                    mock(AccountServiceClient.class), addition, setPort, limit, lockPort);
            return new SelfServiceOnboardingUseCase(tenants, real, resolver, addition, limit,
                    new OnboardingTrustGate(match));
        }

        private AdminOperatorPort.OperatorView facet(String home, String status) {
            AdminOperatorPort.OperatorView v = new AdminOperatorPort.OperatorView(OP, "op-41", home,
                    "owner@acme.com", null, "Owner", status, null, null, Instant.EPOCH, Instant.EPOCH, null,
                    "identity-41");
            operators.operator(v);
            when(resolver.resolve(SUB)).thenReturn(Optional.of(v));
            when(match.match(eq(SUB), anyString())).thenReturn(
                    new VerifiedEmailMatchPort.MatchResult(VerifiedEmailMatchPort.Outcome.MATCHED, Instant.EPOCH));
            return v;
        }

        @Test
        @DisplayName("🔴 AC-3 (표 5행): ACTIVE 회사 측면 · 상한 미만 → 201 · operatorId = 기존 행 · 새 테넌트 TENANT_ADMIN + BILLING 행 + 배정 + ORIGIN 한 행 · 행 잠금 먼저 · 기존 회사 행 바이트 불변 · 캐시 무효화")
        void activeCompanyFacet_belowCap_addsMembershipToThatRow() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null)
                    .row(OP, "SUPPORT_LOCK", "other-co", 5L, null);
            assignments.row(OP, "home-co", null, null).row(OP, "other-co", 5L, List.of("d1"));
            List<InMemoryRoleRowsPort.Row> rolesBefore = new ArrayList<>(operators.rows);
            List<InMemoryAssignmentPort.Row> assignmentsBefore = new ArrayList<>(assignments.rows);

            SelfServiceOnboardingUseCase.Result result =
                    useCase(2).onboard(TENANT, "Acme Corp", SUB, OWNER);

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
            // 🔴 773 OD-4: one ORIGIN row, new tenant only, no granter; 773 OD-3: the lock came first
            verify(setPort).insertGrant(eq(OP), eq(TENANT), eq(ALL_DOMAINS_FULL_ID), eq(DomainGrantSource.ORIGIN),
                    isNull(), isNull(), any());
            verify(setPort, never()).insertGrant(anyLong(), org.mockito.ArgumentMatchers.argThat(t -> !TENANT.equals(t)),
                    anyLong(), anyString(), any(), any(), any());
            verify(lockPort).lockForUpdate(OP);
            verify(evaluatorCache).invalidate("op-41");
        }

        @Test
        @DisplayName("🔴 773 option A: 계정 행 없음 ∧ ACTIVE 회사 측면(iam 자격 운영자) → 이메일 면제(매치 0) · 소속 추가 + ORIGIN")
        void iamCredentialOperator_exempt_addsMembership() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null);
            org.mockito.Mockito.reset(match);

            SelfServiceOnboardingUseCase.Result result =
                    useCase(3).onboard(TENANT, "Acme Corp", SUB, Optional.empty());

            assertThat(result.operatorId()).isEqualTo("op-41");
            verifyNoInteractions(match);
            assertThat(operators.rowsIn(OP, TENANT)).hasSize(2);
            verify(setPort).insertGrant(eq(OP), eq(TENANT), eq(ALL_DOMAINS_FULL_ID), eq(DomainGrantSource.ORIGIN),
                    isNull(), isNull(), any());
        }

        @Test
        @DisplayName("🔴 773 option A: iam 자격 운영자도 상한을 받는다(소유 3 = 상한) → 422 · 테넌트 생성 0")
        void iamCredentialOperator_stillBoundByTheCap() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null)
                    .row(OP, "TENANT_ADMIN", "co-2", null, null)
                    .row(OP, "TENANT_ADMIN", "co-3", null, null);
            org.mockito.Mockito.reset(match);

            assertThatThrownBy(() -> useCase(3).onboard(TENANT, "Acme Corp", SUB, Optional.empty()))
                    .isInstanceOf(OperatorOwnedTenantLimitExceededException.class);
            verifyNoInteractions(tenants, match);
        }

        @Test
        @DisplayName("🔴 773 option A: 계정 행 없음 ∧ '*' 측면 → 409 (면제는 측면 표를 건너뛰지 않는다)")
        void iamCredentialPlatformFacet_409() {
            facet("*", "ACTIVE");
            operators.row(OP, "SUPER_ADMIN", "*", null, null);
            org.mockito.Mockito.reset(match);

            assertThatThrownBy(() -> useCase(3).onboard(TENANT, "Acme Corp", SUB, Optional.empty()))
                    .isInstanceOf(OperatorAlreadyProvisionedException.class);
            verifyNoInteractions(tenants);
        }

        @Test
        @DisplayName("🔴 773 OD-3 경합: 사전 판정은 통과했지만 잠금 뒤 재셈이 상한 → 422 · 쓰기 0 · SUSPEND 보상")
        void capReachedUnderTheLock_compensated() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null)
                    .row(OP, "TENANT_ADMIN", "co-2", null, null);
            // a competing creation commits while this request waits on the row lock
            org.mockito.Mockito.doAnswer(inv -> {
                operators.row(OP, "TENANT_ADMIN", "co-raced", null, null);
                return null;
            }).when(lockPort).lockForUpdate(OP);
            int rowsBefore = operators.rows.size() + 1;

            assertThatThrownBy(() -> useCase(3).onboard(TENANT, "Acme Corp", SUB, OWNER))
                    .isInstanceOf(OperatorOwnedTenantLimitExceededException.class);

            var order = inOrder(tenants, lockPort);
            order.verify(tenants).create(TENANT, "Acme Corp", "B2B_ENTERPRISE");
            order.verify(lockPort).lockForUpdate(OP);
            order.verify(tenants).update(TENANT, null, "SUSPENDED");
            assertThat(operators.rows).hasSize(rowsBefore);
            assertThat(operators.rowsIn(OP, TENANT)).isEmpty();
            verify(setPort, never()).insertGrant(anyLong(), anyString(), anyLong(), anyString(), any(), any(), any());
            verifyNoInteractions(evaluatorCache);
        }

        @Test
        @DisplayName("🔴 (표 4행) 상한 도달(TENANT_ADMIN 테넌트 2, 상한 2) → 422 · 테넌트 생성 0 · 쓰기 0")
        void atCap_422_beforeTenantCreation() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null)
                    .row(OP, "TENANT_ADMIN", "second-co", null, null);
            int rowsBefore = operators.rows.size();

            assertThatThrownBy(() -> useCase(2).onboard(TENANT, "Acme Corp", SUB, OWNER))
                    .isInstanceOf(OperatorOwnedTenantLimitExceededException.class);

            verifyNoInteractions(tenants, evaluatorCache);
            assertThat(operators.rows).hasSize(rowsBefore);
            assertThat(assignments.rows).isEmpty();
        }

        @Test
        @DisplayName("대조군: 같은 두 회사 소유라도 상한 3 이면 통과(773 OD-3 의 값)")
        void cap3_twoOwned_passes() {
            facet("home-co", "ACTIVE");
            operators.row(OP, "TENANT_ADMIN", "home-co", null, null)
                    .row(OP, "TENANT_ADMIN", "second-co", null, null);

            useCase(3).onboard(TENANT, "Acme Corp", SUB, OWNER);

            verify(tenants).create(TENANT, "Acme Corp", "B2B_ENTERPRISE");
            assertThat(operators.rowsIn(OP, TENANT)).hasSize(2);
        }

        @Test
        @DisplayName("🔴 (표 2행) '*' 측면 → 409 OPERATOR_ALREADY_PROVISIONED · 테넌트 생성 0")
        void platformFacet_409() {
            facet("*", "ACTIVE");
            operators.row(OP, "SUPER_ADMIN", "*", null, null);

            assertThatThrownBy(() -> useCase(2).onboard(TENANT, "Acme Corp", SUB, OWNER))
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

            assertThatThrownBy(() -> useCase(2).onboard(TENANT, "Acme Corp", SUB, OWNER))
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
                    new OperatorOwnedTenantLimit(operators, 2), new OnboardingTrustGate(match));

            assertThatThrownBy(() -> uc.onboard(TENANT, "Acme Corp", SUB, OWNER)).isSameAs(boom);

            verify(tenants).create(TENANT, "Acme Corp", "B2B_ENTERPRISE");
            verify(tenants).update(TENANT, null, "SUSPENDED");
            verifyNoInteractions(evaluatorCache);
        }
    }
}
