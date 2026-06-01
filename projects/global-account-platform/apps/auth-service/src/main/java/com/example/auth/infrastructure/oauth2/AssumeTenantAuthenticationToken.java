package com.example.auth.infrastructure.oauth2;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — the assume-tenant token-exchange
 * grant authentication, produced by {@link AssumeTenantAuthenticationConverter}
 * on the SAS token endpoint and consumed by
 * {@link AssumeTenantAuthenticationProvider}.
 *
 * <p>Carries the RFC 8693 request parameters needed to mint the assumed token:
 * the {@code subject_token} (the operator's base GAP OIDC access token), the
 * {@code subject_token_type}, the selected tenant ({@code audience}), and the
 * authenticated client principal (the public {@code platform-console-web} client,
 * already authenticated by the client-auth chain).
 */
public class AssumeTenantAuthenticationToken extends AbstractAuthenticationToken {

    private final Authentication clientPrincipal;
    private final String subjectToken;
    private final String subjectTokenType;
    private final String selectedTenantId;
    private final String selectedTenantType;
    private final String operatorAccountType;

    /**
     * Converter-side constructor — the selected tenant_type is not yet known at
     * the protocol-parse stage (it is resolved by the provider).
     */
    public AssumeTenantAuthenticationToken(Authentication clientPrincipal,
                                           String subjectToken,
                                           String subjectTokenType,
                                           String selectedTenantId) {
        this(clientPrincipal, subjectToken, subjectTokenType, selectedTenantId, null);
    }

    /**
     * Provider-side constructor — carries the resolved selected tenant_type so it
     * survives into the {@link org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext}
     * via {@code getAuthorizationGrant()} (which {@code JwtGenerator} copies).
     */
    public AssumeTenantAuthenticationToken(Authentication clientPrincipal,
                                           String subjectToken,
                                           String subjectTokenType,
                                           String selectedTenantId,
                                           String selectedTenantType) {
        this(clientPrincipal, subjectToken, subjectTokenType, selectedTenantId, selectedTenantType, null);
    }

    /**
     * Provider-side constructor (TASK-BE-329) — additionally carries the operator's
     * {@code account_type} (OPERATOR), read from the validated subject token, so the
     * customizer's token-exchange branch can PRESERVE it on the assumed token (the
     * operator stays OPERATOR while acting for a customer — ADR-MONO-021 D3). It rides
     * the same {@code getAuthorizationGrant()} copy path as {@code selectedTenantType}.
     */
    public AssumeTenantAuthenticationToken(Authentication clientPrincipal,
                                           String subjectToken,
                                           String subjectTokenType,
                                           String selectedTenantId,
                                           String selectedTenantType,
                                           String operatorAccountType) {
        super(Collections.emptyList());
        this.clientPrincipal = clientPrincipal;
        this.subjectToken = subjectToken;
        this.subjectTokenType = subjectTokenType;
        this.selectedTenantId = selectedTenantId;
        this.selectedTenantType = selectedTenantType;
        this.operatorAccountType = operatorAccountType;
        setAuthenticated(false);
    }

    @Override
    public Object getPrincipal() {
        return this.clientPrincipal;
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    public Authentication getClientPrincipal() {
        return clientPrincipal;
    }

    public String getSubjectToken() {
        return subjectToken;
    }

    public String getSubjectTokenType() {
        return subjectTokenType;
    }

    public String getSelectedTenantId() {
        return selectedTenantId;
    }

    public String getSelectedTenantType() {
        return selectedTenantType;
    }

    /**
     * TASK-BE-329 — the operator's {@code account_type} (OPERATOR), carried from the
     * validated subject token so it is PRESERVED on the assumed token (ADR-MONO-021 D3).
     * Null on the converter-side token (resolved by the provider).
     */
    public String getOperatorAccountType() {
        return operatorAccountType;
    }
}
