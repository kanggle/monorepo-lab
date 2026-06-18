package com.example.admin.application;

import com.example.admin.application.exception.SubjectTokenInvalidException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.IamOidcSubjectTokenValidator;
import com.example.admin.infrastructure.client.AuthServiceClient;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-298 / ADR-MONO-014 — port-stub unit coverage for
 * {@link TokenExchangeService}: the OIDC-subject → operator resolver
 * (mapped / unmapped-fail-closed / deactivated) + the fail-closed propagation
 * of a subject-token validation failure.
 *
 * <p>TASK-MONO-295 (ADR-MONO-040 Phase 2): exercises the REAL shared
 * {@link OperatorOidcSubjectResolver} (wrapping the mock {@code operatorPort}) and a
 * mock {@link AuthServiceClient}, proving the login-time exchange resolves an
 * <b>email-seeded</b> operator when the subject token's {@code sub}=account_id and the
 * SAS access token carries no email claim (the email fallback is resolved
 * server-side from the account_id) — the federation-e2e {@code not_provisioned}
 * regression fix.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TokenExchangeService (port-stub unit)")
class TokenExchangeServiceTest {

    @Mock IamOidcSubjectTokenValidator subjectTokenValidator;
    @Mock AdminOperatorPort operatorPort;
    @Mock OperatorAccessTokenIssuer accessTokenIssuer;
    @Mock AuthServiceClient authServiceClient;

    TokenExchangeService service;

    private static final String SUBJECT_TOKEN = "header.payload.sig";
    // Phase 2: the subject-token `sub` is now the account UUID.
    private static final String OIDC_SUB = "acc-uuid-0001";
    private static final String OPERATOR_EMAIL = "op@example.com";
    private static final String OPERATOR_UUID = "00000000-0000-7000-8000-000000000010";

    @BeforeEach
    void setUp() {
        // Use the REAL shared dual-key resolver (the SAME one the assume-tenant gate
        // uses) so resolution is tested through the production component, not stubbed.
        OperatorOidcSubjectResolver resolver = new OperatorOidcSubjectResolver(operatorPort);
        service = new TokenExchangeService(
                subjectTokenValidator, operatorPort, accessTokenIssuer, resolver, authServiceClient);
        // Default: no email resolved (account_id-keyed rows / fail-soft) unless a
        // test overrides it. lenient — not every test reaches the email lookup.
        lenient().when(authServiceClient.resolveOperatorEmail(anyString())).thenReturn(Optional.empty());
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
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
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
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
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
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
        when(operatorPort.findByOidcSubject(OIDC_SUB)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("mapped operator is DISABLED → 401 fail-closed, no token minted")
    void deactivatedOperator_failClosed() {
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("DISABLED", "wms")));

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("mapped operator is LOCKED → 401 fail-closed, no token minted")
    void lockedOperator_failClosed() {
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("LOCKED", "wms")));

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(accessTokenIssuer, never()).mint(anyString());
    }

    @Test
    @DisplayName("subject-token validation failure propagates → no operator lookup, no token minted")
    void invalidSubjectToken_failClosedBeforeLookup() {
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN))
                .thenThrow(new SubjectTokenInvalidException("bad sig"));

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        verify(operatorPort, never()).findByOidcSubject(anyString());
        verify(accessTokenIssuer, never()).mint(anyString());
    }

    // ── TASK-MONO-295 (ADR-MONO-040 Phase 2): DUAL-KEY login-time resolution ─────

    @Test
    @DisplayName("AC-0 (regression fix): sub=account_id misses oidc_subject=email, but server-side email fallback resolves the email-seeded operator → mints (login no longer breaks)")
    void dualKey_accountIdMiss_resolvesViaServerSideEmailFallback() {
        // The exact federation-e2e regression: the subject token's `sub` is the
        // account UUID (Phase 2) and carries NO email claim, while
        // admin_operators.oidc_subject still holds the seed EMAIL. Without the
        // server-side email resolution + dual-key, findByOidcSubject(account_id)
        // misses → 401 → console-web not_provisioned → every operator login breaks.
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
        // account_id key misses (oidc_subject not yet backfilled).
        when(operatorPort.findByOidcSubject(OIDC_SUB)).thenReturn(Optional.empty());
        // The email is resolved SERVER-SIDE from the account_id (auth_db.credentials).
        when(authServiceClient.resolveOperatorEmail(OIDC_SUB)).thenReturn(Optional.of(OPERATOR_EMAIL));
        // Legacy email key hits — oidc_subject == the seed email.
        when(operatorPort.findByOidcSubject(OPERATOR_EMAIL))
                .thenReturn(Optional.of(operatorView("ACTIVE", "wms")));
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.via.email.fallback");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        TokenExchangeService.ExchangeResult result = service.exchange(SUBJECT_TOKEN);

        // The email-seeded operator resolves under sub=account_id with no email claim.
        assertThat(result.accessToken()).isEqualTo("minted.via.email.fallback");
        verify(accessTokenIssuer).mint(OPERATOR_UUID);
    }

    @Test
    @DisplayName("AC-0: account_id key hits → email fallback never consulted (Phase-3 backfill end-state)")
    void dualKey_accountIdHit_emailFallbackNotConsulted() {
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
        when(operatorPort.findByOidcSubject(OIDC_SUB))
                .thenReturn(Optional.of(operatorView("ACTIVE", "wms")));
        when(accessTokenIssuer.mint(OPERATOR_UUID)).thenReturn("minted.via.account.id");
        when(accessTokenIssuer.accessTokenTtlSeconds()).thenReturn(3600L);

        service.exchange(SUBJECT_TOKEN);

        // account_id hit → the legacy email key is never looked up, AND the
        // server-side email resolution (auth-service round-trip) is never invoked
        // (lazy fallback — the happy path pays no extra cost).
        verify(operatorPort, never()).findByOidcSubject(OPERATOR_EMAIL);
        verify(authServiceClient, never()).resolveOperatorEmail(anyString());
    }

    @Test
    @DisplayName("fail-soft email resolve (auth-service down → empty) + account_id miss → 401 fail-closed (invariant unchanged)")
    void emailResolveFailSoft_andAccountIdMiss_failClosed() {
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
        when(operatorPort.findByOidcSubject(OIDC_SUB)).thenReturn(Optional.empty());
        // auth-service unavailable → fail-soft empty (the client swallows the error).
        when(authServiceClient.resolveOperatorEmail(OIDC_SUB)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        // No email key looked up (it was empty); no token minted (fail-closed kept).
        verify(operatorPort, never()).findByOidcSubject(OPERATOR_EMAIL);
        verify(accessTokenIssuer, never()).mint(anyString());
    }
}
