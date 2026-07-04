package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.OperatorAssignmentPort.DelegatedScope;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Collections;
import java.util.List;

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
    private final List<String> orgScope;
    private final DelegatedScope delegatedScope;

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
     * Provider-side constructor (TASK-BE-338, ADR-MONO-020 D3 amendment) —
     * additionally carries the resolved per-assignment {@code org_scope} (the
     * department subtree-root ids the operator may act under within the assumed
     * tenant). Rides the same {@code getAuthorizationGrant()} copy path as
     * {@code selectedTenantType}. {@code null} ⟺ {@code ["*"]} = whole tenant
     * (net-zero) — the customizer maps {@code null}/empty → {@code ["*"]}.
     *
     * <p>TASK-MONO-263 (ADR-032 D5 step 4): the operator's {@code account_type} is no
     * longer carried — the claim is removed entirely.
     */
    public AssumeTenantAuthenticationToken(Authentication clientPrincipal,
                                           String subjectToken,
                                           String subjectTokenType,
                                           String selectedTenantId,
                                           String selectedTenantType,
                                           List<String> orgScope) {
        this(clientPrincipal, subjectToken, subjectTokenType, selectedTenantId,
                selectedTenantType, orgScope, null);
    }

    /**
     * Provider-side constructor (TASK-BE-478, ADR-MONO-045 §3.4 step 2b) —
     * additionally carries the resolved cross-org {@code delegatedScope} cap
     * ({@code delegated ∩ participant ∩ host-holds}) when the assume is
     * partnership-derived host reach. Rides the same {@code getAuthorizationGrant()}
     * copy path as {@code orgScope}. {@code null} for a normal (non-partnership)
     * assignment — the customizer then keeps the BE-338/376 path byte-unchanged.
     */
    public AssumeTenantAuthenticationToken(Authentication clientPrincipal,
                                           String subjectToken,
                                           String subjectTokenType,
                                           String selectedTenantId,
                                           String selectedTenantType,
                                           List<String> orgScope,
                                           DelegatedScope delegatedScope) {
        super(Collections.emptyList());
        this.clientPrincipal = clientPrincipal;
        this.subjectToken = subjectToken;
        this.subjectTokenType = subjectTokenType;
        this.selectedTenantId = selectedTenantId;
        this.selectedTenantType = selectedTenantType;
        this.orgScope = orgScope;
        this.delegatedScope = delegatedScope;
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
     * TASK-BE-338 (ADR-MONO-020 D3 amendment) — the resolved per-assignment
     * {@code org_scope} (department subtree-root ids), carried from the
     * admin-service assignment-check result so the customizer injects the ACTUAL
     * data-scope instead of the hardcoded {@code ["*"]} (TASK-BE-337 v1 bridge).
     * {@code null} ⟺ {@code ["*"]} = whole tenant (net-zero). Null on the
     * converter-side token (resolved by the provider).
     */
    public List<String> getOrgScope() {
        return orgScope;
    }

    /**
     * TASK-BE-478 (ADR-MONO-045 §3.4 step 2b) — the resolved cross-org
     * {@code delegatedScope} cap ({@code delegated ∩ participant ∩ host-holds}),
     * carried from the admin-service assignment-check result so the customizer's
     * assume-tenant branch confines the token's {@code entitled_domains}/{@code roles}
     * to the delegated slice. {@code null} for a normal (non-partnership) assignment —
     * the BE-338/376 path stays byte-unchanged. Null on the converter-side token
     * (resolved by the provider).
     */
    public DelegatedScope getDelegatedScope() {
        return delegatedScope;
    }
}
