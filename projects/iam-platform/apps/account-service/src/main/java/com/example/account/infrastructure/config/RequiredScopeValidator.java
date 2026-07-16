package com.example.account.infrastructure.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates that a JWT presented on {@code /internal/**} carries the required workload scope
 * (default {@code internal.invoke}) — the claim discriminator that distinguishes a GAP
 * {@code client_credentials} <em>system</em> credential from an ordinary user token (TASK-BE-514).
 *
 * <h2>Why this exists</h2>
 * <p>account-service {@code /internal/**} promises a "GAP {@code client_credentials} system credential
 * only" contract. The prior chain pinned only signature + timestamps + issuer and then did
 * {@code .authenticated()} — but the IAM {@code auth-service} SAS is a <b>single, shared</b> issuer that
 * mints BOTH system ({@code client_credentials}) and user ({@code authorization_code}) tokens. So
 * "authenticated" did not distinguish a system credential from a valid CUSTOMER/user token: any token
 * from the shared issuer passed, exposing cross-tenant PII read, GDPR delete, and role mutation
 * (privilege escalation) to any internal-network holder of an ordinary token.
 *
 * <h2>The discriminator</h2>
 * <p>{@code V0019} seeds the {@code internal.invoke} scope to exactly the GAP-internal workload clients
 * (admin/auth/security/account-service-client) and explicitly reserved it for "scope-based hardening in
 * TASK-BE-319". A {@code client_credentials} token minted by those clients carries {@code internal.invoke};
 * an ordinary user token (authorization_code) does not (that scope is never granted to a user-facing
 * client). So requiring {@code internal.invoke} is a positive, fail-closed discriminator that admits the
 * system callers and rejects user tokens — self-maintaining (a new internal caller simply carries the
 * seeded scope), unlike a {@code sub} allow-list that must enumerate every client-id.
 *
 * <p>Runs as a decoder-level {@link OAuth2TokenValidator} (alongside the issuer/timestamp default), so a
 * scope-less token fails verification → {@code 401 UNAUTHORIZED} via the existing
 * {@code SecurityConfig::onAuthenticationFailure} entry point, matching the contract's fail-closed 401.
 */
public final class RequiredScopeValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error MISSING_SCOPE = new OAuth2Error(
            "invalid_token",
            "The token does not carry the required internal workload scope",
            null);

    private final String requiredScope;

    public RequiredScopeValidator(String requiredScope) {
        this.requiredScope = requiredScope == null ? "" : requiredScope.trim();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (requiredScope.isEmpty()) {
            // Fail-closed by construction: a blank required-scope configuration is a wiring error,
            // not a reason to admit everyone. Reject so a mis-configuration surfaces as 401 rather
            // than silently reopening the gap.
            return OAuth2TokenValidatorResult.failure(MISSING_SCOPE);
        }
        if (scopes(token).contains(requiredScope)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(MISSING_SCOPE);
    }

    /**
     * Reads the granted scopes from the {@code scope} claim (SAS emits a space-delimited string;
     * some issuers use a list), falling back to {@code scp}. Absent/blank → empty set → reject.
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
