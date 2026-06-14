package com.example.admin.application;

import com.example.admin.application.exception.SubjectTokenInvalidException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.IamOidcSubjectTokenValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-298 / ADR-MONO-014 — port-stub unit coverage for
 * {@link TokenExchangeService}: the OIDC-subject → operator resolver
 * (mapped / unmapped-fail-closed / deactivated) + the fail-closed propagation
 * of a subject-token validation failure.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("TokenExchangeService (port-stub unit)")
class TokenExchangeServiceTest {

    @Mock IamOidcSubjectTokenValidator subjectTokenValidator;
    @Mock AdminOperatorPort operatorPort;
    @Mock OperatorAccessTokenIssuer accessTokenIssuer;

    @InjectMocks TokenExchangeService service;

    private static final String SUBJECT_TOKEN = "header.payload.sig";
    private static final String OIDC_SUB = "acc-uuid-0001";
    private static final String OPERATOR_UUID = "00000000-0000-7000-8000-000000000010";

    private AdminOperatorPort.OperatorView operatorView(String status, String tenantId) {
        return new AdminOperatorPort.OperatorView(
                42L, OPERATOR_UUID, tenantId,
                "op@example.com", "hash", "Op",
                status, null, null, Instant.now(), Instant.now(), null, null);
    }

    @Test
    @DisplayName("valid subject token + mapped ACTIVE operator → mints via shared issuer; scope NOT from OIDC")
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
    @DisplayName("valid subject token but NO admin_operators mapping → 401 fail-closed, no token minted")
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

        verifyNoInteractions(operatorPort);
        verifyNoInteractions(accessTokenIssuer);
    }
}
