package com.example.admin.application;

import com.example.admin.application.exception.MfaRequiredException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.IamOidcSubjectTokenValidator;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantEntryPolicyManagementPort;
import com.example.admin.application.port.TenantEntryPolicyPort;
import com.example.admin.application.port.TenantProvisioningPort;
import com.example.admin.application.tenant.TenantSummary;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.AdminGrantScopeEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-771 S5 · <b>AC-4</b> (owner decision OD-4 — «등록 유도 · 유예 없음») — the admin-service half of
 * the transition chain, end to end over the PRODUCTION classes with one shared in-memory policy table:
 *
 * <ol>
 *   <li>the management API turns the policy ON ({@link TenantEntryPolicyUseCase#set});</li>
 *   <li>the very next operator token exchange of an operator of that tenant without {@code mfa} in
 *       {@code amr} is refused with {@link MfaRequiredException} — the distinguished {@code 403
 *       MFA_REQUIRED}, never the {@code 401} the console reads as «not an operator»; the assume-tenant
 *       requirement for that tenant flips to {@code true} (a sibling tenant does not);</li>
 *   <li>after enrolment ({@code amr = [pwd, otp, mfa]}) the same exchange issues a token;</li>
 *   <li>turning the policy OFF restores the password-only exchange.</li>
 * </ol>
 *
 * <p>What carries the refusal into enrolment is outside this service and is pinned where it lives
 * (the S5 record in the ticket cites each): console {@code 403 MFA_REQUIRED → /api/auth/step-up →
 * authorize?acr_values=mfa} ({@code mfa-step-up-routes.test.ts}), auth-service {@code acr_values=mfa} with
 * no enrolment → {@code /mfa/setup} ({@code AuthorizeSecondFactorGateTest}), enrolment requires a verified
 * e-mail ({@code MfaPageSliceTest}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AC-4 transition chain — policy ON → refused (MFA_REQUIRED) → enrolled → admitted")
class TenantEntryPolicyTransitionChainTest {

    @Mock IamOidcSubjectTokenValidator subjectTokenValidator;
    @Mock AdminOperatorPort operatorPort;
    @Mock OperatorAccessTokenIssuer accessTokenIssuer;
    @Mock OperatorTenantAssignmentPort assignmentPort;
    @Mock TenantProvisioningPort provisioningPort;
    @Mock AdminGrantScopeEvaluator grantScopeEvaluator;
    @Mock AdminActionAuditor auditor;

    /** The one table both halves read and write — the write must land where the gate reads. */
    final InMemoryEntryPolicyTable table = new InMemoryEntryPolicyTable();

    TenantEntryPolicyUseCase management;
    TokenExchangeService exchange;
    OperatorSecondFactorRequirement requirement;

    static final String TENANT = "acme";
    static final String SIBLING = "acme-labs";
    static final String TENANT_ADMIN_UUID = "00000000-0000-7000-8000-0000000c0a01";
    static final String MEMBER_UUID = "00000000-0000-7000-8000-0000000c0a02";
    static final String MEMBER_SUB = "acc-member-0001";
    static final String TOKEN = "subject.token.jwt";
    static final OperatorContext TENANT_ADMIN = new OperatorContext(TENANT_ADMIN_UUID, "jti");

    @BeforeEach
    void wire() {
        requirement = new OperatorSecondFactorRequirement(operatorPort, assignmentPort, table);
        exchange = new TokenExchangeService(subjectTokenValidator, operatorPort, accessTokenIssuer,
                new OperatorOidcSubjectResolver(operatorPort), requirement);
        management = new TenantEntryPolicyUseCase(table, provisioningPort, operatorPort,
                new TenantScopeGuard(grantScopeEvaluator, auditor), auditor);

        // The tenant admin may manage ITS tenant's policy (a precondition here — lenient; the scope gate's
        // own refusal is asserted in TenantEntryPolicyUseCaseTest); the tenant exists at the authority.
        lenient().when(grantScopeEvaluator.isTenantInAdminScope(TENANT_ADMIN_UUID, Permission.TENANT_SECURITY_MANAGE, TENANT))
                .thenReturn(true);
        when(provisioningPort.get(TENANT)).thenReturn(new TenantSummary(TENANT, "Acme", "B2B_ENTERPRISE", "ACTIVE",
                Instant.EPOCH, Instant.EPOCH));
        when(operatorPort.findByOperatorId(TENANT_ADMIN_UUID)).thenReturn(Optional.of(view(1L, TENANT_ADMIN_UUID)));

        // A plain member operator of the tenant: home = acme, no require_2fa role, no assignment rows.
        when(operatorPort.findByOidcSubject(MEMBER_SUB)).thenReturn(Optional.of(view(2L, MEMBER_UUID)));
        when(operatorPort.anyRoleRequires2fa(anyLong())).thenReturn(false);
        when(assignmentPort.findAssignedTenantIds(anyLong())).thenReturn(Set.of());
        when(accessTokenIssuer.mint(anyString())).thenReturn("operator.jwt");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(900L);
    }

    private static AdminOperatorPort.OperatorView view(long id, String uuid) {
        return new AdminOperatorPort.OperatorView(id, uuid, TENANT, uuid + "@acme.test", null, "Op", "ACTIVE",
                null, null, Instant.EPOCH, Instant.EPOCH, null, null);
    }

    private void loginWith(String... amr) {
        when(subjectTokenValidator.validate(TOKEN))
                .thenReturn(new IamOidcSubjectTokenValidator.ValidatedSubject(MEMBER_SUB, Set.of(amr)));
    }

    @Test
    @DisplayName("🔴 AC-4: OFF → pwd admitted · ON → pwd refused MFA_REQUIRED (assume flips for acme only) · mfa admitted · OFF → pwd admitted")
    void policyOn_refusesWithoutMfa_thenEnrolmentAdmits() {
        // 0. Control — no policy row yet: a password-only login is admitted (nothing changed for anyone).
        loginWith("pwd");
        assertThat(exchange.exchange(TOKEN).accessToken()).isEqualTo("operator.jwt");
        assertThat(requirement.requiredForAssume(view(2L, MEMBER_UUID), TENANT)).isFalse();

        // 1. The tenant admin turns the entry policy ON (no grace period — OD-4).
        assertThat(management.set(TENANT_ADMIN, TENANT, true, "회사 보안 정책").requireMfa()).isTrue();

        // 2. The very next exchange without a second factor is refused with the DISTINGUISHED error —
        //    the one the console routes to step-up → IAM enrolment (never the 401 «not an operator»).
        assertThatExceptionOfType(MfaRequiredException.class).isThrownBy(() -> exchange.exchange(TOKEN));
        //    …and assume-tenant into acme now requires it too; the sibling tenant is untouched (control).
        assertThat(requirement.requiredForAssume(view(2L, MEMBER_UUID), TENANT)).isTrue();
        assertThat(requirement.requiredForAssume(view(2L, MEMBER_UUID), SIBLING)).isFalse();

        // 3. After enrolment the IdP issues amr = [pwd, otp, mfa] — the same operator is admitted.
        loginWith("pwd", "otp", "mfa");
        assertThat(exchange.exchange(TOKEN).accessToken()).isEqualTo("operator.jwt");

        // 4. Turning the policy OFF restores the password-only exchange (the row is kept with FALSE).
        management.set(TENANT_ADMIN, TENANT, false, "rollback");
        loginWith("pwd");
        assertThat(exchange.exchange(TOKEN).accessToken()).isEqualTo("operator.jwt");
        assertThat(table.find(TENANT)).get().extracting(TenantEntryPolicyManagementPort.EntryPolicyView::requireMfa)
                .isEqualTo(false);
    }

    /**
     * A faithful in-memory table behind BOTH ports (as the JPA adapter is in production) — same
     * «row absent = off», «'*' never holds» rules.
     */
    static final class InMemoryEntryPolicyTable implements TenantEntryPolicyPort, TenantEntryPolicyManagementPort {
        private final Map<String, EntryPolicyView> rows = new HashMap<>();

        @Override
        public Set<String> findTenantsRequiringMfa(Collection<String> tenantIds) {
            Set<String> on = new LinkedHashSet<>();
            for (String t : tenantIds == null ? List.<String>of() : tenantIds) {
                EntryPolicyView v = t == null ? null : rows.get(t);
                if (v != null && v.requireMfa()) on.add(t);
            }
            return on;
        }

        @Override
        public Optional<EntryPolicyView> find(String tenantId) {
            return Optional.ofNullable(rows.get(tenantId));
        }

        @Override
        public EntryPolicyView save(String tenantId, boolean requireMfa, Long updatedByInternalId, Instant at) {
            if ("*".equals(tenantId)) throw new IllegalStateException("CHECK (tenant_id <> '*')");
            EntryPolicyView v = new EntryPolicyView(tenantId, requireMfa, at, "writer-" + updatedByInternalId);
            rows.put(tenantId, v);
            return v;
        }
    }
}
