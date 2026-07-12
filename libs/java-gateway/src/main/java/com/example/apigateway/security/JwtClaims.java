package com.example.apigateway.security;

import com.example.security.oauth2.TenantClaimValidator;
import java.util.Collection;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Claim extractors shared by every gateway's header enrichment.
 *
 * <p>These were duplicated verbatim across wms / scm / fan. {@link #role(Jwt)} is the one
 * that matters: its precedence and its <em>empty-string</em> fallback are a security
 * contract, and three copies of a security contract is how one of them silently stops
 * matching the others.
 */
public final class JwtClaims {

    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_SCOPE = "scope";

    private JwtClaims() {}

    public static String subject(Jwt jwt) {
        return jwt.getSubject();
    }

    public static String email(Jwt jwt) {
        return jwt.getClaimAsString(CLAIM_EMAIL);
    }

    public static String tenantId(Jwt jwt) {
        return jwt.getClaimAsString(TenantClaimValidator.CLAIM_TENANT_ID);
    }

    /** The {@code scope} claim: a space-delimited string per RFC 6749. */
    public static String scope(Jwt jwt) {
        return jwt.getClaimAsString(CLAIM_SCOPE);
    }

    /**
     * Resolves a role claim with defined precedence:
     * {@code roles} (array, joined on {@code ","}) → {@code role} (string) → {@code ""}.
     *
     * <p><strong>Never returns {@code null}</strong>, and the no-role case is the empty
     * string rather than an absent header. That is deliberate: downstream services must
     * read {@code X-User-Role: ""} as "no authorized role" and deny. Omitting the header
     * instead would let a service that forgot to handle the absent case fall through to a
     * default — the failure would be silent and it would be open.
     */
    public static String role(Jwt jwt) {
        Collection<String> multi = jwt.getClaimAsStringList(CLAIM_ROLES);
        if (multi != null && !multi.isEmpty()) {
            return String.join(",", multi);
        }
        Object single = jwt.getClaim(CLAIM_ROLE);
        if (single instanceof String s && !s.isBlank()) {
            return s;
        }
        return "";
    }
}
