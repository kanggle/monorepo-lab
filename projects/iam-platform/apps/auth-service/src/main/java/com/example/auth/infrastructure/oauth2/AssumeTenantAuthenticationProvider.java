package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.exception.AssumeTenantDeniedException;
import com.example.auth.application.port.OperatorAssignmentPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — provider for the assume-tenant
 * RFC 8693 token-exchange grant. Mirrors the
 * {@link SasRefreshTokenAuthenticationProvider} wiring pattern: it is constructed
 * lazily inside {@code AuthorizationServerConfig} after SAS init so it can use the
 * shared {@link OAuth2TokenGenerator} (which applies
 * {@link TenantClaimTokenCustomizer}).
 *
 * <p>Issuance steps (highest-risk auth hot-path — follow literally):
 * <ol>
 *   <li><b>Validate subject token</b> with the service's OWN {@link JwtDecoder}
 *       (the same JWKS it signs with — the subject token is auth-service's own
 *       base GAP OIDC token). Extract {@code sub} (account_id). Any validation
 *       failure → {@code invalid_grant} (fail-closed).</li>
 *   <li><b>Fail-CLOSED assignment gate</b>: call
 *       {@link OperatorAssignmentPort#resolveAssignment} (admin-service). Any
 *       failure (not-assigned / admin down / timeout / circuit-open) throws
 *       {@link AssumeTenantDeniedException} → {@code invalid_grant}, no token. The
 *       result also carries the per-assignment {@code org_scope} (TASK-BE-338)
 *       which rides onto the resolved grant for the customizer to inject.</li>
 *   <li><b>Mint</b> through the shared {@link OAuth2TokenGenerator} +
 *       {@link TenantClaimTokenCustomizer} so the assumed token has the SAME
 *       {@code iss}/kid as the login token. The selected tenant +
 *       {@code tenant_type} are carried on the token context so the customizer's
 *       token-exchange branch injects {@code tenant_id=<selected>} +
 *       {@code entitled_domains=<selected's ACTIVE subs>} (D3, fail-soft).</li>
 *   <li><b>No refresh token</b> — the assumed token is short-lived, re-minted per
 *       selection (ADR-020 § 3.1).</li>
 * </ol>
 *
 * <p>Customer tenants are {@code B2B_ENTERPRISE} (multi-tenancy.md: customer
 * tenants are enterprise tenants). The provider carries that on the context so
 * the customizer never blanks {@code tenant_type} (auth-service fails closed on a
 * missing tenant_type).
 *
 * <p><b>TASK-MONO-299 (ADR-MONO-040 Phase 3 part B) — account_id-only operator
 * resolution.</b> The Phase-2 transitional DUAL-KEY (account_id first, legacy email
 * fallback resolved server-side from {@code auth_db.credentials}) is removed now that
 * the part-A email→account_id backfill (TASK-MONO-298) has migrated
 * {@code admin_operators.oidc_subject} to the account UUID. The validated {@code sub}
 * IS the account UUID (jwt-standard-claims.md), so the assignment gate keys on it
 * directly — no email resolution, no {@link OperatorAssignmentPort} email param.
 */
@Slf4j
public class AssumeTenantAuthenticationProvider implements AuthenticationProvider {

    /** Customer tenants are enterprise tenants (multi-tenancy.md). */
    static final String CUSTOMER_TENANT_TYPE = "B2B_ENTERPRISE";

    private final JwtDecoder subjectTokenDecoder;
    private final OperatorAssignmentPort operatorAssignmentPort;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    public AssumeTenantAuthenticationProvider(
            JwtDecoder subjectTokenDecoder,
            OperatorAssignmentPort operatorAssignmentPort,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
        this.subjectTokenDecoder = subjectTokenDecoder;
        this.operatorAssignmentPort = operatorAssignmentPort;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        AssumeTenantAuthenticationToken exchange = (AssumeTenantAuthenticationToken) authentication;

        OAuth2ClientAuthenticationToken clientPrincipal =
                getAuthenticatedClientElseThrowInvalidClient(exchange.getClientPrincipal());
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();
        if (registeredClient == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        // TASK-MONO-721 (ADR-MONO-076 — 갈래 D): a WORKLOAD credential takes a different
        // branch. It has no account and no operator assignment, so the fail-closed gate below
        // (admin-service) is the wrong question for it; its gate is WorkloadTenantCatalog.
        // 🔴 Enumeration in that catalog is what selects the branch, and a client that is
        // absent keeps exactly the behaviour it had before this ticket: it falls through to
        // the operator path and is denied there, because a client id is not an assigned
        // operator. Both refuse — this branch refuses for a reason the catalog records.
        if (WorkloadTenantCatalog.isWorkloadExchangeClient(registeredClient.getClientId())) {
            return authenticateWorkload(exchange, clientPrincipal, registeredClient);
        }

        // --- 1. Validate the subject token (auth-service's own JWKS) — fail-closed. ---
        String oidcSubject;
        try {
            Jwt subjectJwt = subjectTokenDecoder.decode(exchange.getSubjectToken());
            oidcSubject = subjectJwt.getSubject();
            // TASK-BE-376 (ADR-MONO-035 O1 / step 4a): the operator's domain roles are
            // no longer preserved from the subject token (TASK-BE-370) — the base
            // operator token has no domain-role set to preserve. The customizer's
            // assume-tenant branch now DERIVES the `roles` from the SELECTED tenant's
            // entitled domains (OperatorRoleDerivation), so no roles are extracted here.
            // TASK-MONO-263 (ADR-032 D5 step 4): the operator's account_type is no
            // longer read or preserved — the claim is removed entirely.
        } catch (JwtException e) {
            log.debug("assume-tenant: subject_token validation failed (fail-closed): {}", e.toString());
            throw invalidGrant("subject_token is invalid");
        }
        if (oidcSubject == null || oidcSubject.isBlank()) {
            throw invalidGrant("subject_token has no subject");
        }

        String selectedTenantId = exchange.getSelectedTenantId();

        // --- 2. FAIL-CLOSED assignment gate (admin-service). ---
        // ANY failure (not-assigned / admin down / timeout / circuit-open) throws
        // AssumeTenantDeniedException → invalid_grant. NOT fail-soft.
        // TASK-BE-338: the result ALSO carries the selected assignment's org_scope
        // (subtree-root ids; null ⟺ ["*"] net-zero) which rides onto the resolved
        // grant for the customizer to inject.
        java.util.List<String> orgScope;
        OperatorAssignmentPort.DelegatedScope delegatedScope;
        try {
            // TASK-MONO-299 (ADR-MONO-040 Phase 3 part B): account_id-only — the
            // validated `sub` IS the account UUID and admin_operators.oidc_subject is
            // backfilled to account_id (part A), so admin-service resolves on it directly.
            OperatorAssignmentPort.AssignmentResult assignment =
                    operatorAssignmentPort.resolveAssignment(oidcSubject, selectedTenantId);
            orgScope = assignment.orgScope();
            // TASK-BE-478 (ADR-MONO-045 §3.4 step 2b): the additive cross-org
            // partnership cap (non-null ONLY for partnership-derived host reach) rides
            // onto the resolved grant so the customizer confines the token's
            // entitled_domains/roles to the delegated slice. null for a normal
            // assignment → the BE-338/376 path stays byte-unchanged.
            delegatedScope = assignment.delegatedScope();
        } catch (AssumeTenantDeniedException e) {
            log.debug("assume-tenant: assignment gate denied (fail-closed): {}", e.getMessage());
            throw invalidGrant("operator is not assigned to the selected tenant");
        }

        // --- 3. Mint through the shared JwtGenerator + TenantClaimTokenCustomizer. ---
        // The customizer's token-exchange branch reads the selected tenant + type
        // from the authorizationGrant (the AssumeTenantAuthenticationToken), which
        // JwtGenerator copies verbatim into the JwtEncodingContext — unlike
        // arbitrary context.put() attributes, which it does NOT copy. We rebuild the
        // grant carrying the resolved tenant_type so it survives the copy.
        // TASK-BE-338: carry the resolved org_scope (null ⟺ ["*"] net-zero) on the
        // grant so the customizer's assume-tenant branch injects the ACTUAL
        // data-scope rather than the hardcoded ["*"] (TASK-BE-337 bridge).
        // TASK-BE-376 (ADR-MONO-035 O1 / step 4a): the operator's `roles` are no longer
        // threaded from the subject token — the customizer's assume-tenant branch
        // DERIVES them from the selected tenant's entitled domains
        // (OperatorRoleDerivation), reusing the existing entitled_domains fetch (no
        // extra account-service call). org_scope (BE-338) plumbing is unchanged.
        // TASK-MONO-263 (ADR-032 D5 step 4): account_type is no longer carried.
        // TASK-BE-478 (ADR-MONO-045 §3.4 step 2b): also carry the resolved cross-org
        // delegatedScope cap (null for a normal assignment) so the customizer's
        // assume-tenant branch confines entitled_domains/roles to the delegated slice.
        // TASK-MONO-515 (ADR-MONO-060 option A): carry the VALIDATED subject account
        // UUID (`oidcSubject`, read out of the subject token in step 1 and already used
        // as the assignment-gate key) so the customizer can set the assumed token's
        // `sub` to it. Without this the customizer has no route to the account — the
        // token-exchange principal is the CLIENT — and `sub` stayed the client id, which
        // made every console operator indistinguishable downstream (all six gateways map
        // X-User-Id <- sub).
        AssumeTenantAuthenticationToken resolvedGrant = new AssumeTenantAuthenticationToken(
                clientPrincipal, exchange.getSubjectToken(), exchange.getSubjectTokenType(),
                selectedTenantId, CUSTOMER_TENANT_TYPE, orgScope, delegatedScope, oidcSubject);

        // TASK-BE-336: propagate the client's REGISTERED scopes into the
        // domain-facing token's `scope` claim (was Set.of() — empty). This is
        // the scope-based delegation model (ADR-MONO-020 / ADR-001): the
        // platform-console-web client is granted `erp.write` (V0023) so the
        // assumed token can carry it, letting erp masterdata-service authorize a
        // department WRITE (WRITE = erp.write ∨ operator-role; entitlement-trust
        // widens READ only — ADR-MONO-019 § D5). The erp tenant gate still
        // rejects the write on a non-erp tenant, so carrying erp.write on a
        // non-erp assumed token is inert (no over-grant). The base
        // authorization_code token is unchanged (the console requests only
        // openid/profile/email/tenant.read at authorize) — write capability
        // rides ONLY in the tenant-scoped assumed token (least-privilege).
        DefaultOAuth2TokenContext.Builder contextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(clientPrincipal)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(registeredClient.getScopes())
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .authorizationGrant(resolvedGrant);

        OAuth2TokenContext accessTokenContext = contextBuilder.build();
        OAuth2Token generated = tokenGenerator.generate(accessTokenContext);
        if (generated == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generated.getTokenValue(),
                generated.getIssuedAt(),
                generated.getExpiresAt(),
                Set.of());

        // No refresh token for the assumed token (short-lived, re-minted per selection).
        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient, clientPrincipal, accessToken, null, Map.of());
    }

    /**
     * TASK-MONO-721 (ADR-MONO-076 D1/D2/D4) — the <b>workload</b> assume-tenant branch.
     *
     * <p>Reached only for a client enumerated in {@link WorkloadTenantCatalog}. A client that is
     * absent falls through to the operator branch above, where the fail-closed assignment gate
     * denies it (a client id is not an assigned operator) — so both paths refuse, and the
     * difference is only which one records the reason.
     *
     * <p>🔴 It does <b>not</b> call {@link OperatorAssignmentPort}. A workload has no operator
     * assignment; asking admin-service about one would be asking the wrong question and would
     * couple a machine-to-machine path to a service it has no business depending on.
     *
     * <p>Four gates, each fail-closed, in this order:
     * <ol>
     *   <li><b>The subject token is valid</b> — decoded with this service's own JWKS, the same
     *       decoder the operator branch uses.</li>
     *   <li><b>The subject token belongs to the presenting client</b> — its {@code aud} must
     *       contain this client id. 🔴 This gate is what stops client A presenting client B's
     *       token to borrow B's granted scopes: without it, gate 3 would read a scope set that
     *       was never granted to A. {@code jwt-standard-claims.md} guarantees the binding
     *       ("{@code aud} — the client id of the registered client the token was issued to — on
     *       every grant, {@code client_credentials} included"), so this reads a contract rather
     *       than a convention.</li>
     *   <li><b>The request carries a scope the client is registered for</b> — the minted token
     *       carries the <em>intersection</em>, not the client's full registered set. This is
     *       ADR-MONO-061's second constraint carried onto the tenant axis by ADR-MONO-076
     *       § Context: the registration must not decide the token, the request does. 🔴 Note the
     *       difference from the operator branch above, which deliberately mints the client's
     *       REGISTERED scopes (TASK-BE-336, a different decision for a different principal).</li>
     *   <li><b>The target tenant is one this client may assume</b> — {@link WorkloadTenantCatalog}.
     *       An enumerated client asking for a tenant outside its set is refused HERE, at the
     *       issuer, with {@code invalid_grant}: no token is minted. TASK-MONO-721 AC-1's control
     *       measures exactly this, and AC-7 fixes the shape of the refusal.</li>
     * </ol>
     */
    private Authentication authenticateWorkload(AssumeTenantAuthenticationToken exchange,
                                                OAuth2ClientAuthenticationToken clientPrincipal,
                                                RegisteredClient registeredClient) {
        String clientId = registeredClient.getClientId();
        String selectedTenantId = exchange.getSelectedTenantId();

        // --- 1. Subject token validity (this service's own JWKS) — fail-closed. ---
        Jwt subjectJwt;
        try {
            subjectJwt = subjectTokenDecoder.decode(exchange.getSubjectToken());
        } catch (JwtException e) {
            log.debug("assume-tenant(workload): subject_token validation failed (fail-closed): {}",
                    e.toString());
            throw invalidGrant("subject_token is invalid");
        }

        // --- 2. The subject token was issued TO this client. ---
        List<String> audience = subjectJwt.getAudience();
        if (audience == null || !audience.contains(clientId)) {
            log.warn("SECURITY: assume-tenant(workload) subject_token does not belong to the "
                    + "presenting client. clientId={}, aud={}", clientId, audience);
            throw invalidGrant("subject_token was not issued to this client");
        }

        // --- 3. The request's granted scopes, intersected with the registration. ---
        Set<String> grantedScopes = intersectScopes(subjectJwt, registeredClient);
        if (grantedScopes.isEmpty()) {
            log.debug("assume-tenant(workload): no registered scope on the subject token. "
                    + "clientId={}", clientId);
            throw invalidGrant("subject_token carries no scope this client is registered for");
        }

        // --- 4. May this client assume that tenant? ---
        if (!WorkloadTenantCatalog.mayAssume(clientId, selectedTenantId)) {
            log.warn("SECURITY: assume-tenant(workload) refused — clientId={} may not assume "
                    + "tenant={} (assumable={})",
                    clientId, selectedTenantId, WorkloadTenantCatalog.assumableTenants(clientId));
            throw invalidGrant("client is not permitted to assume the requested tenant");
        }

        // --- 5. Mint. A DIFFERENT grant type, so the operator derivations are unreachable. ---
        WorkloadAssumeTenantAuthenticationToken resolvedGrant =
                new WorkloadAssumeTenantAuthenticationToken(
                        clientPrincipal, clientId, selectedTenantId, CUSTOMER_TENANT_TYPE);

        OAuth2TokenContext accessTokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(clientPrincipal)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizedScopes(grantedScopes)
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.TOKEN_EXCHANGE)
                .authorizationGrant(resolvedGrant)
                .build();

        OAuth2Token generated = tokenGenerator.generate(accessTokenContext);
        if (generated == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generated.getTokenValue(),
                generated.getIssuedAt(),
                generated.getExpiresAt(),
                grantedScopes);

        log.debug("assume-tenant(workload): minted for clientId={} tenant={} scopes={}",
                clientId, selectedTenantId, grantedScopes);

        // No refresh token — same as the operator path (ADR-MONO-020 § 3.1), and
        // ADR-MONO-076 keeps it that way: a long-lived assumed workload token would
        // make a catalog change take effect only after it expired.
        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient, clientPrincipal, accessToken, null, Map.of());
    }

    /**
     * The scopes on the subject token that this client is actually registered for.
     *
     * <p>RFC 6749 puts {@code scope} on the wire as a space-delimited string; this issuer mints
     * it as a JSON array. 🔴 Both shapes are read — a reader that handles only one returns an
     * empty set for the other, and an empty set here means {@code invalid_grant}, so the wrong
     * shape would look exactly like "the caller asked for nothing".
     */
    private static Set<String> intersectScopes(Jwt subjectJwt, RegisteredClient registeredClient) {
        Set<String> onToken = new java.util.LinkedHashSet<>();
        Object raw = subjectJwt.getClaim("scope");
        if (raw instanceof Collection<?> list) {
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) {
                    onToken.add(o.toString());
                }
            }
        } else if (raw != null) {
            for (String s : raw.toString().split("\\s+")) {
                if (!s.isBlank()) {
                    onToken.add(s);
                }
            }
        }
        onToken.retainAll(registeredClient.getScopes());
        return onToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return AssumeTenantAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private OAuth2ClientAuthenticationToken getAuthenticatedClientElseThrowInvalidClient(
            Authentication authentication) {
        if (authentication instanceof OAuth2ClientAuthenticationToken clientAuth
                && clientAuth.isAuthenticated()) {
            return clientAuth;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }

    private static OAuth2AuthenticationException invalidGrant(String description) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, description, null));
    }
}
