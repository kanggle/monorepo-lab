package com.example.security.jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * ADR-MONO-025 — canonical reader for the cross-domain ABAC <b>data-scope</b>
 * claim, shared by every domain that scopes an operator's data visibility.
 *
 * <p>The data-scope is a signed JWT claim carrying an array of <b>opaque scope
 * tokens</b> the owning domain interprets (erp: department subtree-root ids;
 * wms: warehouse ids; finance: accounting-unit ids; …). The producer copies the
 * tokens verbatim from {@code operator_tenant_assignment.org_scope}; neither the
 * producer nor IAM interprets them.
 *
 * <p><b>Claim aliases (read in order):</b> {@code data_scope} (canonical) then
 * {@code org_scope} (legacy alias — the original erp name; consumers dual-read so
 * the producer never has to migrate). Each raw value may be a JSON array, a
 * delimited string, or absent.
 *
 * <p><b>Semantics (verified against the erp reference adapter,
 * RoleScopeAuthorizationAdapter):</b>
 * <ul>
 *   <li><b>Unrestricted</b> — the wildcard token {@code "*"} is present
 *       ({@link #isUnrestricted()}). The producer maps an UNSCOPED assignment
 *       ({@code org_scope} NULL) to {@code ["*"]}, so this is the net-zero
 *       default — every operator who has not been deliberately scoped carries
 *       {@code ["*"]} and sees everything, exactly as before data-scoping.</li>
 *   <li><b>Scoped</b> — a non-empty token set WITHOUT {@code "*"}: the domain
 *       filters to rows reachable from those tokens; <b>deny-by-default</b> for
 *       anything outside ({@link #allows(String)} returns {@code false}).</li>
 *   <li><b>Empty / absent</b> — NOT unrestricted: {@link #isUnrestricted()} is
 *       {@code false} and {@link #allows(String)} denies everything. This is the
 *       <b>fail-closed</b> defensive case; a correctly minted token always
 *       carries at least {@code ["*"]}, so an empty scope means "deny", never
 *       "allow all".</li>
 * </ul>
 *
 * <p>Data-scope is <b>narrowing only</b> — it can never widen what an already
 * RBAC-authorised, tenant-authorised operator may reach; it composes with (does
 * not replace) the permission and tenant checks. See
 * {@code platform/abac-data-scope.md}.
 *
 * <p>Framework-agnostic by design (operates on raw claim values, not Spring's
 * {@code Jwt}) so resource-server and non-Spring consumers share one
 * implementation.
 */
public final class AbacDataScope {

    /** Canonical claim name. */
    public static final String CLAIM_DATA_SCOPE = "data_scope";
    /** Legacy alias (original erp name); consumers dual-read both. */
    public static final String CLAIM_ORG_SCOPE = "org_scope";
    /** Platform/unrestricted wildcard token. */
    public static final String WILDCARD = "*";

    private final Set<String> tokens;

    private AbacDataScope(Set<String> tokens) {
        this.tokens = tokens;
    }

    /**
     * Parse the raw claim values in alias order (typically
     * {@code data_scope} then {@code org_scope}); each may be a {@link Collection},
     * a delimited {@link String} (split on commas/whitespace), {@code null}, or a
     * scalar. Blank tokens are dropped; tokens are trimmed; the union is returned.
     *
     * @param rawValues the raw claim values (e.g. {@code jwt.getClaim("data_scope")},
     *                  {@code jwt.getClaim("org_scope")})
     * @return a never-null {@code AbacDataScope}
     */
    public static AbacDataScope fromClaimValues(Object... rawValues) {
        Set<String> out = new LinkedHashSet<>();
        if (rawValues != null) {
            for (Object raw : rawValues) {
                if (raw == null) {
                    continue;
                }
                if (raw instanceof Collection<?> c) {
                    for (Object v : c) {
                        addToken(out, v == null ? null : String.valueOf(v));
                    }
                } else if (raw instanceof String s) {
                    for (String part : s.split("[,\\s]+")) {
                        addToken(out, part);
                    }
                } else {
                    addToken(out, String.valueOf(raw));
                }
            }
        }
        return new AbacDataScope(Set.copyOf(out));
    }

    private static void addToken(Set<String> out, String s) {
        if (s != null) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
    }

    /** The parsed scope tokens (wildcard included if present); never null. */
    public Set<String> tokens() {
        return tokens;
    }

    /** {@code true} when no tokens were present (fail-closed: denies everything). */
    public boolean isEmpty() {
        return tokens.isEmpty();
    }

    /**
     * {@code true} iff the wildcard {@code "*"} is present — platform/unrestricted
     * scope (the net-zero default the producer emits for unscoped assignments).
     * An EMPTY scope is NOT unrestricted.
     */
    public boolean isUnrestricted() {
        return tokens.contains(WILDCARD);
    }

    /**
     * Whether {@code token} is in scope: {@code true} when unrestricted or the
     * token is explicitly listed; {@code false} otherwise (deny-by-default,
     * including the empty/absent fail-closed case).
     */
    public boolean allows(String token) {
        return isUnrestricted() || (token != null && tokens.contains(token));
    }
}
