package com.example.apigateway.testfixtures;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Shared builder for the minimal {@link Jwt} that gateway filter tests authenticate with.
 *
 * <p>Collapses three near-identical private {@code jwt(...)} copies in finance's gateway-service
 * suite (TASK-MONO-429). The {@code alg} and {@code ttl} deltas are kept as parameters on purpose:
 * {@code RoleAdmissionFilterTest} deliberately uses an <em>unsigned</em> {@code alg="none"} token
 * (admission runs after signature verification, so it must exercise a token the resource server
 * would have rejected on its own) while the enrichment / tenant-gate suites use RS256. A builder
 * that always signed RS256 would silently change what the admission test exercises, so the default
 * overload signs RS256 / 60s and callers that differ pass their delta explicitly.
 */
public final class GatewayTestJwts {

    private static final String DEFAULT_ALG = "RS256";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
    private static final String ISSUER = "http://iam.local";

    private GatewayTestJwts() {}

    /** RS256, 60s TTL — the common case for enrichment / tenant-gate suites. */
    public static Jwt jwt(Map<String, Object> claims) {
        return jwt(DEFAULT_ALG, DEFAULT_TTL, claims);
    }

    /**
     * Full form. Pass {@code alg="none"} for the unsigned admission token; pass a longer {@code ttl}
     * where the copy did.
     */
    public static Jwt jwt(String alg, Duration ttl, Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", alg)
                .issuer(ISSUER)
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(ttl));
        claims.forEach(b::claim);
        return b.build();
    }
}
