package com.example.admin.application;

import com.example.admin.application.exception.SubjectTokenInvalidException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.IamOidcSubjectTokenValidator;
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
                subjectTokenValidator, operatorPort, accessTokenIssuer, resolver);
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

    // ── TASK-MONO-299 (ADR-MONO-040 Phase 3 part B): account_id-only resolution ──

    @Test
    @DisplayName("account_id-only: sub=account_id resolves the backfilled operator → mints")
    void accountIdOnly_resolvesBackfilledOperator() {
        // Phase 3: admin_operators.oidc_subject is backfilled to account_id (part A),
        // so findByOidcSubject(account_id) hits directly — no email fallback needed.
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
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
        when(subjectTokenValidator.validateAndExtractSubject(SUBJECT_TOKEN)).thenReturn(OIDC_SUB);
        when(operatorPort.findByOidcSubject(OIDC_SUB)).thenReturn(Optional.empty());

        assertThatExceptionOfType(SubjectTokenInvalidException.class)
                .isThrownBy(() -> service.exchange(SUBJECT_TOKEN));

        // Only the account_id lookup is attempted; the legacy email key is never
        // looked up (the dual-key fallback is removed); no token minted.
        verify(operatorPort, never()).findByOidcSubject(OPERATOR_EMAIL);
        verify(accessTokenIssuer, never()).mint(anyString());
    }
}
