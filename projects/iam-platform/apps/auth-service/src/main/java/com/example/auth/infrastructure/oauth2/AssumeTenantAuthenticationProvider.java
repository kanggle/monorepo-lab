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

        // --- 1. Validate the subject token (auth-service's own JWKS) — fail-closed. ---
        String oidcSubject;
        String subjectEmail;
        try {
            Jwt subjectJwt = subjectTokenDecoder.decode(exchange.getSubjectToken());
            oidcSubject = subjectJwt.getSubject();
            // TASK-MONO-295 (ADR-MONO-040 Phase 2): the SAS access-token `sub` is now
            // the account UUID (Phase 2 flipped it off the login email). But
            // admin_operators.oidc_subject is seeded with the operator's EMAIL, and
            // the account_id<->email mapping is cross-DB (auth_db vs admin_db) so it
            // cannot be backfilled in one Flyway step. Carry the subject token's
            // `email` claim as the DUAL-KEY legacy fallback: the assignment gate
            // resolves on account_id (`sub`) first, then on this email — keeping every
            // existing operator's tenant switch working through the migration.
            subjectEmail = subjectJwt.getClaimAsString("email");
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
        try {
            // TASK-MONO-295 (ADR-MONO-040 Phase 2): DUAL-KEY — pass both the account_id
            // (`sub`) and the legacy email so admin-service resolves the operator on
            // account_id first, email second (the seed value).
            OperatorAssignmentPort.AssignmentResult assignment =
                    operatorAssignmentPort.resolveAssignment(oidcSubject, subjectEmail, selectedTenantId);
            orgScope = assignment.orgScope();
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
        AssumeTenantAuthenticationToken resolvedGrant = new AssumeTenantAuthenticationToken(
                clientPrincipal, exchange.getSubjectToken(), exchange.getSubjectTokenType(),
                selectedTenantId, CUSTOMER_TENANT_TYPE, orgScope);

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
