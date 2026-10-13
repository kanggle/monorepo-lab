package com.example.admin.application;

import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.AdminOperatorRoleJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.AdminRolePermissionJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OrgNodeSubtreeResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shared tenant gate ({@link QueryTenantScopeGate}) over a REAL {@link AdminGrantScopeEvaluator} whose
 * repositories are mocked — so the role-row → grant-scope resolution these cells depend on is the production
 * code, not a stub.
 *
 * <p>TASK-MONO-784 S3 (ADR-MONO-082 b · D-4 · admin-api.md § 행동 테넌트): the tenant is judged against the
 * permission's GRANT scope (the role rows conferring it), never the memberships or the home; omitted tenant →
 * {@code '*'} / the one tenant / 403 / 400. The unit-level half of AC-1 lives here (the IT half is
 * {@code OperatorMembershipReadAxisIntegrationTest}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QueryTenantScopeGate 단위 테스트 (784 S3 — grant scope)")
class QueryTenantScopeGateTest {

    @Mock AdminOperatorJpaRepository operators;
    @Mock AdminOperatorRoleJpaRepository operatorRoles;
    @Mock AdminRolePermissionJpaRepository rolePermissions;
    @Mock OrgNodeSubtreeResolver subtreeResolver;
    @Mock AdminActionAuditor auditor;

    private QueryTenantScopeGate gate;

    /** role id → the permissions it confers (the rbac.md seed matrix, the cells used here). */
    private static final Map<Long, List<String>> ROLE_PERMS = Map.of(
            1L, List.of("operator.manage", "audit.read", "account.read", "account.lock"), // SUPER_ADMIN (subset)
            2L, List.of("operator.manage", "tenant.admin.delegate"),                      // TENANT_ADMIN
            3L, List.of("account.read", "audit.read", "security.event.read"),              // SUPPORT_READONLY
            4L, List.of("account.lock", "account.unlock", "account.force_logout", "audit.read")); // SUPPORT_LOCK

    private final List<AdminOperatorRoleJpaEntity> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        AdminGrantScopeEvaluator evaluator =
                new AdminGrantScopeEvaluator(operators, operatorRoles, rolePermissions, subtreeResolver);
        gate = new QueryTenantScopeGate(evaluator, auditor);

        AdminOperatorJpaEntity op = mock(AdminOperatorJpaEntity.class);
        when(op.getId()).thenReturn(7L);
        when(op.getStatus()).thenReturn("ACTIVE");
        when(operators.findByOperatorId("op-1")).thenReturn(Optional.of(op));
        when(operatorRoles.findByOperatorId(7L)).thenReturn(rows);
        when(rolePermissions.findRoleIdsGrantingPermission(anyString(), anyCollection())).thenAnswer(inv -> {
            String perm = inv.getArgument(0);
            Collection<Long> ids = inv.getArgument(1);
            return ids.stream().filter(id -> ROLE_PERMS.getOrDefault(id, List.of()).contains(perm)).distinct().toList();
        });
    }

    private void role(long roleId, String tenant) {
        rows.add(AdminOperatorRoleJpaEntity.create(7L, roleId, Instant.EPOCH, null, tenant));
    }

    private static OperatorContext op() {
        return new OperatorContext("op-1", "jti-1");
    }

    // ── AC-1 (unit half): a role in A does not answer in B ──────────────────────────────────────────

    /**
     * 🔴 AC-1 control, failure side first. The operator holds TENANT_ADMIN + SUPPORT_READONLY + SUPPORT_LOCK @
     * alpha and is a member of beta with NO role row there (the memberships are invisible to this gate — that is
     * the point). Before S3 the gate admitted any membership (home ∪ assignments), so beta passed.
     */
    @Test
    @DisplayName("🔴 AC-1: role rows only @alpha → beta refused for operator.manage · audit.read · account.lock (+ DENIED row)")
    void ac1_betaRefused_alphaAdmitted() {
        role(2L, "alpha");
        role(3L, "alpha");
        role(4L, "alpha");

        // failure side first
        assertThatThrownBy(() -> gate.resolveWithoutDenyRow(op(), "beta", Permission.OPERATOR_MANAGE))
                .isInstanceOf(TenantScopeDeniedException.class);
        assertThatThrownBy(() -> gate.resolve(op(), "beta", ActionCode.AUDIT_QUERY, Permission.AUDIT_READ))
                .isInstanceOf(TenantScopeDeniedException.class);
        assertThatThrownBy(() -> gate.resolve(op(), "beta", ActionCode.ACCOUNT_LOCK, Permission.ACCOUNT_LOCK))
                .isInstanceOf(TenantScopeDeniedException.class);
        verify(auditor).recordCrossTenantDenied(any(), isNull(), eq(ActionCode.AUDIT_QUERY), eq("audit.read"), eq("beta"));
        verify(auditor).recordCrossTenantDenied(any(), isNull(), eq(ActionCode.ACCOUNT_LOCK), eq("account.lock"), eq("beta"));

        // passing side — same operator, alpha
        assertThat(gate.resolveWithoutDenyRow(op(), "alpha", Permission.OPERATOR_MANAGE).tenantId()).isEqualTo("alpha");
        assertThat(gate.resolve(op(), "alpha", ActionCode.AUDIT_QUERY, Permission.AUDIT_READ).tenantId()).isEqualTo("alpha");
        QueryTenantScopeGate.Resolved lock = gate.resolve(op(), "alpha", ActionCode.ACCOUNT_LOCK, Permission.ACCOUNT_LOCK);
        assertThat(lock.tenantId()).isEqualTo("alpha");
        assertThat(lock.isPlatformScope()).isFalse();
    }

    @Test
    @DisplayName("the grant scope is per permission: SUPPORT_LOCK @beta opens beta's lock, not beta's operator list")
    void scopeIsPerPermission() {
        role(2L, "alpha");
        role(4L, "beta");

        assertThat(gate.resolve(op(), "beta", ActionCode.ACCOUNT_LOCK, Permission.ACCOUNT_LOCK).tenantId())
                .isEqualTo("beta");
        assertThatThrownBy(() -> gate.resolveWithoutDenyRow(op(), "beta", Permission.OPERATOR_MANAGE))
                .isInstanceOf(TenantScopeDeniedException.class);
        verify(auditor, never()).recordCrossTenantDenied(any(), any(), any(), anyString(), anyString());
    }

    // ── omitted tenant ──────────────────────────────────────────────────────────────────────────────

    /** Control: a single-company operator's omitted tenant is its one tenant — the old «home» default. */
    @Test
    @DisplayName("control: omitted, grant scope = {alpha} → alpha (byte-identical to the home default of a single-company operator)")
    void omitted_single_isThatTenant() {
        role(4L, "alpha");

        QueryTenantScopeGate.Resolved r = gate.resolve(op(), null, ActionCode.AUDIT_QUERY, Permission.AUDIT_READ);
        assertThat(r.tenantId()).isEqualTo("alpha");
        assertThat(r.isPlatformScope()).isFalse();
        assertThat(gate.resolve(op(), " ", ActionCode.AUDIT_QUERY, Permission.AUDIT_READ).tenantId()).isEqualTo("alpha");
    }

    @Test
    @DisplayName("omitted, grant scope = {alpha, beta} → 400 (IllegalArgumentException → VALIDATION_ERROR), no DENIED row")
    void omitted_two_is400() {
        role(3L, "alpha");
        role(3L, "beta");

        assertThatThrownBy(() -> gate.resolve(op(), null, ActionCode.AUDIT_QUERY, Permission.AUDIT_READ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId is required");
        verify(auditor, never()).recordCrossTenantDenied(any(), any(), any(), anyString(), any());
    }

    @Test
    @DisplayName("omitted, grant scope empty → 403 TENANT_SCOPE_DENIED (+ DENIED row with no attempted tenant)")
    void omitted_none_is403() {
        role(4L, "alpha"); // SUPPORT_LOCK — no operator.manage anywhere

        assertThatThrownBy(() -> gate.resolve(op(), null, ActionCode.ACCOUNT_SEARCH, Permission.ACCOUNT_READ))
                .isInstanceOf(TenantScopeDeniedException.class);
        verify(auditor).recordCrossTenantDenied(any(), isNull(), eq(ActionCode.ACCOUNT_SEARCH), eq("account.read"), isNull());
    }

    @Test
    @DisplayName("platform grant ('*' row): omitted → '*' · explicit tenant → admitted · isPlatformScope = true")
    void platform() {
        role(1L, "*");

        QueryTenantScopeGate.Resolved omitted = gate.resolve(op(), null, ActionCode.AUDIT_QUERY, Permission.AUDIT_READ);
        assertThat(omitted.tenantId()).isEqualTo("*");
        assertThat(omitted.isPlatformScope()).isTrue();
        QueryTenantScopeGate.Resolved explicit = gate.resolve(op(), "ecommerce", ActionCode.AUDIT_QUERY, Permission.AUDIT_READ);
        assertThat(explicit.tenantId()).isEqualTo("ecommerce");
        assertThat(explicit.isPlatformScope()).isTrue();
    }

    @Test
    @DisplayName("non-platform operator asking for '*' → 403")
    void nonPlatform_star_refused() {
        role(3L, "alpha");
        assertThatThrownBy(() -> gate.resolve(op(), "*", ActionCode.AUDIT_QUERY, Permission.AUDIT_READ))
                .isInstanceOf(TenantScopeDeniedException.class);
    }

    @Test
    @DisplayName("unknown operator → empty scope → 403")
    void unknownOperator() {
        assertThatThrownBy(() -> gate.resolve(new OperatorContext("ghost", null), null,
                ActionCode.ACCOUNT_SEARCH, Permission.ACCOUNT_READ))
                .isInstanceOf(TenantScopeDeniedException.class);
    }

    // ── no permission key (accounts e-mail · operator lookup) ───────────────────────────────────────

    @Test
    @DisplayName("🔴 R2: any-role gate — beta (member, no role) refused; alpha (any role) admitted; omitted → alpha")
    void anyRole() {
        role(4L, "alpha");

        assertThatThrownBy(() -> gate.resolveAnyRole(op(), "beta", ActionCode.ACCOUNT_SEARCH, Permission.ACCOUNT_READ))
                .isInstanceOf(TenantScopeDeniedException.class);
        assertThat(gate.resolveAnyRole(op(), "alpha", ActionCode.ACCOUNT_SEARCH, Permission.ACCOUNT_READ).tenantId())
                .isEqualTo("alpha");
        assertThat(gate.resolveAnyRole(op(), null, ActionCode.ACCOUNT_SEARCH, Permission.ACCOUNT_READ).tenantId())
                .isEqualTo("alpha");
    }
}
