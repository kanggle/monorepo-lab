package com.example.admin.application;

import com.example.admin.application.exception.MfaRequiredException;
import com.example.admin.application.exception.SecondFactorRequirementUnavailableException;
import com.example.admin.application.exception.SubjectTokenInvalidException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.IamOidcSubjectTokenValidator;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.application.port.TenantEntryPolicyPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-298 / ADR-MONO-014 — port-stub unit coverage for
 * {@link TokenExchangeService}: the OIDC-subject → operator resolver
 * (mapped / unmapped-fail-closed / deactivated) + the fail-closed propagation
 * of a subject-token validation failure.
 *
 * <p>TASK-MONO-299 (ADR-MONO-040 Phase 3 part B): exercises the REAL shared
 * account_id-only {@link OperatorOidcSubjectResolver} (wrapping the mock
 * {@code operatorPort}). The subject-token {@code sub} is the account UUID and
 * {@code admin_operators.oidc_subject} is backfilled to account_id, so the operator
 * resolves by it directly (the Phase-2 email fallback is removed).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TokenExchangeService (port-stub unit)")
class TokenExchangeServiceTest {

    @Mock IamOidcSubjectTokenValidator subjectTokenValidator;
    @Mock AdminOperatorPort operatorPort;
    @Mock OperatorAccessTokenIssuer accessTokenIssuer;
    // TASK-MONO-771 S4 — the second-factor requirement's inputs (real component, mocked reads).
    @Mock OperatorTenantAssignmentPort assignmentPort;
    @Mock TenantEntryPolicyPort entryPolicyPort;

    TokenExchangeService service;

    private static final String SUBJECT_TOKEN = "header.payload.sig";
    // The subject-token `sub` is the account UUID (jwt-standard-claims.md).
    private static final String OIDC_SUB = "acc-uuid-0001";
    private static final String OPERATOR_EMAIL = "op@example.com";
    private static final String OPERATOR_UUID = "00000000-0000-7000-8000-000000000010";

    @BeforeEach
    void setUp() {
        // Use the REAL shared account_id-only resolver (the SAME one the assume-tenant
        // gate uses) so resolution is tested through the production component.
        OperatorOidcSubjectResolver resolver = new OperatorOidcSubjectResolver(operatorPort);
        service = new TokenExchangeService(
                subjectTokenValidator, operatorPort, accessTokenIssuer, resolver,
                new OperatorSecondFactorRequirement(operatorPort, assignmentPort, entryPolicyPort));
    }

    /** A validated subject whose token carried {@code amr} = the given values. */
    private static IamOidcSubjectTokenValidator.ValidatedSubject subject(String sub, String... amr) {
        return new IamOidcSubjectTokenValidator.ValidatedSubject(sub, Set.of(amr));
    }

    private AdminOperatorPort.OperatorView operatorView(String status, String tenantId) {
        return new AdminOperatorPort.OperatorView(
                42L, OPERATOR_UUID, tenantId,
                OPERATOR_EMAIL, "hash", "Op",
                status, null, null, Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("valid subject token + mapped ACTIVE operator (account_id key) → mints via shared issuer; scope NOT from OIDC")
    void validExchange_mintsOperatorToken() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "wms")));
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.operator.jwt");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        TokenExchangeService.ExchangeResult result = service.exchange(SUBJECT_TOKEN);

        assertThat(result.accessToken()).isEqualTo("minted.operator.jwt");
        assertThat(result.expiresIn()).isEqualTo(3600L);
        // The operator UUID handed to the issuer is the resolved operator row's
        // — never anything derived from the OIDC token.
        verify(accessTokenIssuer).mint(OPERATOR_UUID);
    }

    @Test
    @DisplayName("SUPER_ADMIN operator → scope sentinel comes from the row, issuer still gets only operator UUID")
    void superAdmin_scopeFromRowNotOidc() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "*")));
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.super.jwt");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        TokenExchangeService.ExchangeResult result = service.exchange(SUBJECT_TOKEN);

        assertThat(result.accessToken()).isEqualTo("minted.super.jwt");
        // The issuer is told only the operator UUID; tenant scope ('*') is
        // resolved later from admin_operators.tenant_id, never injected here
        // and never read from the OIDC token.
        verify(accessTokenIssuer).mint(OPERATOR_UUID);
    }

    @Test
    @DisplayName("valid subject token but NO admin_operators mapping (neither key) → 401 fail-closed, no token minted")
    void noMapping_failClosed() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB));
        when(operatorPort.findByOidcSubject(OIDC_SUB)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("mapped operator is DISABLED → 401 fail-closed, no token minted")
    void deactivatedOperator_failClosed() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("DISABLED", "wms")));

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("mapped operator is LOCKED → 401 fail-closed, no token minted")
    void lockedOperator_failClosed() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("LOCKED", "wms")));

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("subject-token validation failure propagates → no operator lookup, no token minted")
    void invalidSubjectToken_failClosedBeforeLookup() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN))
                .thenThrow(new SubjectTokenInvalidException("bad sig"));

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(operatorPort, never()).findByOidcSubject(anyString());
        verify(accessTokenIssuer, never()).mint(anyString());
    }

    // ── TASK-MONO-299 (ADR-MONO-040 Phase 3 part B): account_id-only resolution ──

    @Test
    @DisplayName("account_id-only: sub=account_id resolves the backfilled operator → mints")
    void accountIdOnly_resolvesBackfilledOperator() {
        // Phase 3: admin_operators.oidc_subject is backfilled to account_id (part A),
        // so findByOidcSubject(account_id) hits directly — no email fallback needed.
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "wms")));
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.via.account.id");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        TokenExchangeService.ExchangeResult result = service.exchange(SUBJECT_TOKEN);

        assertThat(result.accessToken()).isEqualTo("minted.via.account.id");
        verify(accessTokenIssuer).mint(OPERATOR_UUID);
    }

    @Test
    @DisplayName("account_id-only: account_id miss → 401 fail-closed (no email fallback exists)")
    void accountIdOnly_miss_failClosed() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB));
        when(operatorPort.findByOidcSubject(OIDC_SUB)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        // Only the account_id lookup is attempted; the legacy email key is never
        // looked up (the dual-key fallback is removed); no token minted.
        verify(operatorPort, never()).findByOidcSubject(OPERATOR_EMAIL);
        verify(accessTokenIssuer, never()).mint(anyString());
    }

    // ── TASK-MONO-771 S4 (ADR-MONO-080 D4 · R2, OD-2 · OD-3): second-factor requirement ──────────
    //
    // AC-1 «before» (recorded in the ticket § S4): against the pre-S4 code, a require_2fa operator
    // whose subject token carried no "mfa" WAS minted an operator token (a temporary test, rc=0,
    // deleted after the flip). The tests below are the «after».

    @Test
    @DisplayName("AC-1: require_2fa role + amr=[pwd] (no mfa) → 403 MFA_REQUIRED, no token minted")
    void ac1_require2faRole_withoutMfa_refused() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB, "pwd"));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "*")));
        when(operatorPort.anyRoleRequires2fa(42L)).thenReturn(true);

        assertThatExceptionOfType(MfaRequiredException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("AC-1: require_2fa role + NO amr claim at all → 403 MFA_REQUIRED (absent = no second factor)")
    void ac1_require2faRole_amrAbsent_refused() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "*")));
        when(operatorPort.anyRoleRequires2fa(42L)).thenReturn(true);

        assertThatExceptionOfType(MfaRequiredException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("AC-1: require_2fa role + amr ∋ mfa → minted; no requirement read is needed at all")
    void ac1_require2faRole_withMfa_minted() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN))
                .thenReturn(subject(OIDC_SUB, "pwd", "otp", "mfa"));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "*")));
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.after.mfa");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        assertThat(service.exchange(SUBJECT_TOKEN).accessToken()).isEqualTo("minted.after.mfa");
        verify(operatorPort, never()).anyRoleRequires2fa(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("AC-1 control: no require_2fa role, no policy → amr=[pwd] still minted (net-zero)")
    void ac1_control_noRequirement_minted() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB, "pwd"));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "wms")));
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.no.requirement");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        assertThat(service.exchange(SUBJECT_TOKEN).accessToken()).isEqualTo("minted.no.requirement");
    }

    @Test
    @DisplayName("OD-3: an ASSIGNED tenant has its entry policy ON → 403 MFA_REQUIRED; with mfa → minted")
    void od3_assignedTenantPolicyOn_refusedThenMintedWithMfa() {
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "acme-corp")));
        when(operatorPort.anyRoleRequires2fa(42L)).thenReturn(false);
        when(assignmentPort.findAssignedTenantIds(42L)).thenReturn(Set.of("globex"));
        when(entryPolicyPort.findTenantsRequiringMfa(Set.of("acme-corp", "globex")))
                .thenReturn(Set.of("globex"));
        when(subjectTokenValidator.validate(SUBJECT_TOKEN))
                .thenReturn(subject(OIDC_SUB, "pwd"))
                .thenReturn(subject(OIDC_SUB, "pwd", "otp", "mfa"));
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.policy.mfa");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        assertThatExceptionOfType(MfaRequiredException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));
        assertThat(service.exchange(SUBJECT_TOKEN).accessToken()).isEqualTo("minted.policy.mfa");
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("OD-3: home '*' contributes nothing — the policy read sees the assignment rows only")
    void od3_platformHome_contributesNothing() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB, "pwd"));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "*")));
        when(operatorPort.anyRoleRequires2fa(42L)).thenReturn(false);
        when(assignmentPort.findAssignedTenantIds(42L)).thenReturn(Set.of("acme-corp"));
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.platform");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        assertThat(service.exchange(SUBJECT_TOKEN).accessToken()).isEqualTo("minted.platform");

        org.mockito.ArgumentCaptor<java.util.Collection<String>> asked =
                org.mockito.ArgumentCaptor.forClass(java.util.Collection.class);
        verify(entryPolicyPort).findTenantsRequiringMfa(asked.capture());
        assertThat(asked.getValue()).containsExactly("acme-corp").doesNotContain("*");
    }

    @Test
    @DisplayName("OD-3: partnerships are NOT in the admin scope — a policy-ON partnership host does not refuse the exchange")
    void od3_partnershipHostExcluded() {
        // The policy table knows only a host this operator reaches via a partnership (not an
        // assignment). The requirement never reads partnerships, so the host is never asked about.
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB, "pwd"));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "partner-co")));
        when(operatorPort.anyRoleRequires2fa(42L)).thenReturn(false);
        when(assignmentPort.findAssignedTenantIds(42L)).thenReturn(Set.of());
        when(entryPolicyPort.findTenantsRequiringMfa(org.mockito.ArgumentMatchers.anyCollection()))
                .thenAnswer(inv -> {
                    java.util.Collection<String> ids = inv.getArgument(0);
                    return ids.contains("host-co") ? Set.of("host-co") : Set.of();
                });
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.partner");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        assertThat(service.exchange(SUBJECT_TOKEN).accessToken()).isEqualTo("minted.partner");
    }

    @Test
    @DisplayName("fail-closed: role read fails → SecondFactorRequirementUnavailable (500), no token, never 401/403")
    void readFailure_role_failClosed500() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB, "pwd"));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "wms")));
        when(operatorPort.anyRoleRequires2fa(42L))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        assertThatExceptionOfType(SecondFactorRequirementUnavailableException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));
        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("fail-closed: policy read fails → SecondFactorRequirementUnavailable (500), no token")
    void readFailure_policy_failClosed500() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB, "pwd"));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "wms")));
        when(operatorPort.anyRoleRequires2fa(42L)).thenReturn(false);
        when(assignmentPort.findAssignedTenantIds(42L)).thenReturn(Set.of());
        when(entryPolicyPort.findTenantsRequiringMfa(org.mockito.ArgumentMatchers.anyCollection()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"));

        assertThatExceptionOfType(SecondFactorRequirementUnavailableException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));
        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("401 unchanged: unmapped subject without mfa → 401 (SubjectTokenInvalid), requirement never read")
    void unmapped_stays401_beforeRequirement() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB, "pwd"));
        when(operatorPort.findByOidcSubject(OIDC_SUB)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));
        verify(operatorPort, never()).anyRoleRequires2fa(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("401 unchanged: DISABLED operator holding a require_2fa role → 401, not 403")
    void disabledRequire2faOperator_stays401() {
        when(subjectTokenValidator.validate(SUBJECT_TOKEN)).thenReturn(subject(OIDC_SUB, "pwd"));
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("DISABLED", "*")));

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));
        verify(operatorPort, never()).anyRoleRequires2fa(org.mockito.ArgumentMatchers.anyLong());
    }
}
