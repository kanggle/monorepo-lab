package com.example.apigateway.filter;

import com.example.apigateway.security.JwtClaims;
import java.util.function.Predicate;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Admission predicates for {@link RoleAdmissionFilter}. Generic credential math only — which
 * platform, which role names, which surface is the wiring site's to know, the same division
 * {@link JwtHeaderMapping} keeps for enrichment. No domain vocabulary lives here.
 */
public final class RoleAdmissions {

    private RoleAdmissions() {}

    /**
     * Admit iff the token carries an authorization credential the platform recognizes: a
     * non-blank <em>role</em> (human/operator tokens) <strong>or</strong> a non-blank
     * <em>scope</em> (machine / {@code client_credentials} tokens). A token carrying neither
     * is unauthorized for the surface and is rejected — the rule-6 defence
     * ({@code jwt-standard-claims.md} § 6).
     *
     * <p><strong>Why role <em>or</em> scope, not role alone.</strong> The platform authorizes
     * on two axes: humans by {@code roles}, machines by OAuth {@code scope}. scm is
     * backend-only in v1, so {@code client_credentials} tokens — {@code scope} present, no
     * {@code roles} — are its <em>primary</em> caller shape
     * ({@code scm/specs/integration/iam-integration.md} Edge Case E3), and its gateway
     * admission is documented there (line: "valid token but insufficient scope/role → 403
     * FORBIDDEN") as exactly this "has scope or role" check. A role-only gate would 403 that
     * legitimate machine traffic. erp and finance likewise authorize services on scope
     * ({@code erp.read}/{@code erp.write} etc.), so the same superset is the safe, behaviour-
     * preserving reading of rule 6 for every operator gateway.
     *
     * <p><strong>Why presence, not a named role set.</strong> Tokens are {@code aud}-bound to
     * one platform (rule 5) and these gateways route a single surface (no path split, unlike
     * ecommerce's {@code /api/admin/**}). Every role a platform issues is therefore valid for
     * its one surface, so "carries a credential" <em>is</em> the closed-set check rule 6 asks
     * for. Presence is also robust where a platform issues more than one operator-role
     * vocabulary — scm's services check {@code OPERATOR}/{@code ADMIN}/{@code SUPER_ADMIN}
     * while iam's assume-tenant flow mints {@code SCM_OPERATOR} (TASK-SCM-BE-029): both are
     * admitted, neither is silently rejected. And it admits the {@code SUPER_ADMIN} wildcard
     * incident-response token, which a hand-enumerated set could forget and thereby cut the
     * incident-response path.
     */
    public static Predicate<Jwt> roleOrScope() {
        return jwt -> hasRole(jwt) || hasScope(jwt);
    }

    /** A non-blank resolved role ({@link JwtClaims#role}: {@code roles[]} → {@code role} → ""). */
    private static boolean hasRole(Jwt jwt) {
        return !JwtClaims.role(jwt).isBlank();
    }

    /** A non-blank {@code scope} claim (RFC 6749 space-delimited; present on machine tokens). */
    private static boolean hasScope(Jwt jwt) {
        String scope = JwtClaims.scope(jwt);
        return scope != null && !scope.isBlank();
    }
}
