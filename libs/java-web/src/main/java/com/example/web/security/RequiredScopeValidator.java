package com.example.web.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A decoder-level {@link OAuth2TokenValidator} that requires a JWT to carry a specific granted scope.
 *
 * <h2>Why this exists</h2>
 * <p>When a single OAuth2 authorization server (a shared issuer) mints BOTH machine tokens
 * ({@code client_credentials}) AND end-user tokens ({@code authorization_code}), pinning only the issuer
 * and signature does <b>not</b> distinguish a system credential from a valid user token — both are
 * "authenticated". An internal / service-to-service endpoint that means to admit only workload callers
 * must therefore require a positive claim discriminator. Requiring a workload-only scope (one that is
 * granted to service clients but never to user-facing clients) is such a discriminator: it is a positive
 * assertion (not a fail-open "absence" check) and is self-maintaining — a new workload caller simply
 * carries the seeded scope, with no client-id allow-list to keep in sync.
 *
 * <h2>Behaviour</h2>
 * <ul>
 *   <li>Success iff the token's {@code scope} claim (an OAuth2 space-delimited string, or a list;
 *       falling back to {@code scp}) contains the configured required scope.</li>
 *   <li>A blank/empty required scope is treated as a wiring error and <b>fails closed</b> (rejects all) —
 *       a mis-configuration must surface as a rejection rather than silently admitting everyone.</li>
 * </ul>
 *
 * <p>Compose it with the issuer/timestamp default via {@code DelegatingOAuth2TokenValidator} on a
 * {@code NimbusJwtDecoder}. A token failing this validator fails token verification, so the caller's
 * existing resource-server / filter entry point answers its usual fail-closed status (e.g. 401 or 403).
 * The class is framework-agnostic (no servlet / spring-web types) and works on both servlet and reactive
 * stacks.
 */
public final class RequiredScopeValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error MISSING_SCOPE = new OAuth2Error(
            "invalid_token",
            "The token does not carry the required scope",
            null);

    private final String requiredScope;

    public RequiredScopeValidator(String requiredScope) {
        this.requiredScope = requiredScope == null ? "" : requiredScope.trim();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (requiredScope.isEmpty()) {
            // Fail-closed by construction: a blank required-scope configuration is a wiring error, not a
            // reason to admit everyone. Reject so a mis-configuration surfaces rather than reopening the gap.
            return OAuth2TokenValidatorResult.failure(MISSING_SCOPE);
        }
        if (scopes(token).contains(requiredScope)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(MISSING_SCOPE);
    }

    /**
     * Reads the granted scopes from the {@code scope} claim (an OAuth2 space-delimited string; some
     * issuers emit a list), falling back to {@code scp}. Absent/blank/unexpected shape → empty set → reject.
     */
    private static Set<String> scopes(Jwt token) {
        Object claim = token.getClaims().get("scope");
        if (claim == null) {
            claim = token.getClaims().get("scp");
        }
        if (claim instanceof String s) {
            if (s.isBlank()) {
                return Set.of();
            }
            return new HashSet<>(Arrays.asList(s.trim().split("\\s+")));
        }
        if (claim instanceof Collection<?> c) {
            Set<String> out = new HashSet<>();
            for (Object o : c) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        }
        return Set.of();
    }
}
