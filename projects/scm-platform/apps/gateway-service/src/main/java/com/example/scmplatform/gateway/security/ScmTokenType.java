package com.example.scmplatform.gateway.security;

import com.example.apigateway.security.JwtClaims;
import java.util.Collection;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Distinguishes machine (client_credentials) callers from human ones, for the
 * {@code X-Token-Type} header scm alone injects (TASK-SCM-BE-001 Edge Case E1).
 *
 * <p>This lives in scm, not in {@code libs/java-gateway}, and that is the point of the
 * mapping-list design: the library takes a {@code Function<Jwt,String>} and never needs to
 * know what a client_credentials token is. A shared "enrichment config" of booleans would
 * have forced this heuristic into the library as a branch only one of four gateways ever
 * takes — an untaken path in a production security filter (ADR-MONO-048 § D4).
 */
public final class ScmTokenType {

    public static final String CLIENT_CREDENTIALS = "client_credentials";
    public static final String USER = "user";

    private ScmTokenType() {}

    /** {@code "client_credentials"} or {@code "user"} — never null; safe for an ALWAYS mapping. */
    public static String of(Jwt jwt) {
        return isClientCredentials(jwt) ? CLIENT_CREDENTIALS : USER;
    }

    /**
     * Heuristic for the client_credentials grant:
     * <ul>
     *   <li>no {@code email} claim (RFC 6749 / OIDC client_credentials carries no user
     *       identity claims), <strong>and</strong></li>
     *   <li>either {@code azp == sub} (Spring Authorization Server sets {@code azp} to the
     *       client_id, and for client_credentials the {@code sub} is the client_id too), or
     *       the shape of a machine token: a {@code scope} claim and no roles at all.</li>
     * </ul>
     *
     * <p>Deliberately conservative. A false positive — treating a human's token as a
     * machine's — is far worse than a false negative, so every branch here has to be
     * positively established before it says {@code client_credentials}.
     */
    public static boolean isClientCredentials(Jwt jwt) {
        if (jwt.getClaimAsString(JwtClaims.CLAIM_EMAIL) != null) {
            return false;
        }
        String azp = jwt.getClaimAsString("azp");
        String sub = jwt.getSubject();
        if (azp != null && azp.equals(sub)) {
            return true;
        }
        Collection<String> roles = jwt.getClaimAsStringList(JwtClaims.CLAIM_ROLES);
        boolean hasRolesArray = roles != null && !roles.isEmpty();
        boolean hasRoleString = jwt.getClaimAsString(JwtClaims.CLAIM_ROLE) != null;
        boolean hasScope = jwt.getClaimAsString(JwtClaims.CLAIM_SCOPE) != null;
        return hasScope && !hasRolesArray && !hasRoleString;
    }
}
