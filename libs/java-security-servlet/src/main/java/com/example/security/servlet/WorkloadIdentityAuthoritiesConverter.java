package com.example.security.servlet;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Authentication converter for an {@code /internal/**} (workload-identity) Resource Server chain.
 * Grants {@link #ROLE_INTERNAL} to a machine token that carries a required workload scope.
 *
 * <p>Wire it into the internal chain, supplying <em>the consuming service's own</em> scope:
 *
 * <pre>{@code
 * .oauth2ResourceServer(rs -> rs
 *         .jwt(jwt -> jwt
 *                 .decoder(internalJwtDecoder)
 *                 .jwtAuthenticationConverter(
 *                         new WorkloadIdentityAuthoritiesConverter(REQUIRED_WORKLOAD_SCOPE))))
 * }</pre>
 *
 * <h2>Mechanism here, policy in the service</h2>
 *
 * This class owns only the <b>mechanism</b>: read the token's scopes in whichever of the three
 * shapes the issuer emits, compare against one required scope, grant or withhold one authority.
 * <b>Which</b> scope opens <b>which</b> surface is a per-service decision and is supplied by the
 * caller — deliberately, because this is a security verdict: a shared class that also owned the
 * policy would let one service's relaxation propagate to every other consumer. It is also why no
 * scope string literal may be added to this file: {@code libs/} is project-agnostic
 * (HARDSTOP-03, {@code platform/shared-library-policy.md}).
 *
 * <p>Choosing that scope is not clerical. It must be one only a machine can hold — an end-user
 * <em>resource</em> scope that merely reads like the right name (e.g. a {@code <product>.<res>.read}
 * granted to a web client) makes the discriminator discriminate nothing, because every logged-in
 * user would then clear {@code ROLE_INTERNAL}. Each consumer records its own reasoning next to its
 * constant. See {@code platform/security-rules.md}: an internal-only surface MUST require a claim
 * only a machine can carry — exactly one of subject allow-list OR required scope; this class is the
 * required-scope half.
 *
 * <h2>Do NOT gate on {@code tenant_id} absence</h2>
 *
 * Every IAM token — including the {@code client_credentials} grant — carries {@code tenant_id}
 * ({@code platform/contracts/jwt-standard-claims.md}: "issued on every grant; a token without it is
 * rejected at the edge"), and the issuer's tenant-claim customizer stamps it fail-closed and cannot
 * suppress it. A "reject if {@code tenant_id} present" check is therefore an unsanctioned
 * <em>negative</em> discriminator that rejects the real machine token — it shipped green once only
 * because a test helper minted a fabricated {@code tenant_id}-less token, and every access check
 * 403'd in the live topology (TASK-FAN-BE-029). This note travels with the code so the reason
 * cannot be lost and the check re-added.
 *
 * <h2>Behaviour at the chain level</h2>
 *
 * A request with no token never reaches this converter (401 at the entry point). A token that
 * lacks the required scope gets no authority, so the chain's {@code .hasRole("INTERNAL")} gate
 * answers 403. This class never throws and never rejects — it only decides whether to grant.
 *
 * <p>Promoted from two byte-identical service copies by TASK-MONO-521. It lives in the servlet
 * module rather than {@code libs:java-security} for the same reason
 * {@link com.example.security.servlet.actor.ActorContextJwtAuthenticationConverter} does: the
 * blocking {@code Converter<Jwt, AbstractAuthenticationToken>} shape is the servlet Resource
 * Server's contract (the reactive one takes {@code Converter<Jwt, Mono<AbstractAuthenticationToken>>}),
 * and {@code libs:java-security} is consumed by six reactive gateways that must not acquire it.
 */
public class WorkloadIdentityAuthoritiesConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    private final String requiredWorkloadScope;

    /**
     * @param requiredWorkloadScope the machine-only scope that opens this service's
     *                              {@code /internal/**} surface; must be non-blank
     * @throws IllegalArgumentException if the scope is null or blank — a missing scope is a wiring
     *                                  error, and failing at construction surfaces it at boot
     *                                  rather than as a silent 403 on every internal call
     */
    public WorkloadIdentityAuthoritiesConverter(String requiredWorkloadScope) {
        if (requiredWorkloadScope == null || requiredWorkloadScope.isBlank()) {
            throw new IllegalArgumentException(
                    "requiredWorkloadScope must be non-blank — an internal chain with no scope to "
                            + "match on cannot authorize anything, and a blank one would silently "
                            + "deny every caller instead of announcing the misconfiguration.");
        }
        this.requiredWorkloadScope = requiredWorkloadScope;
    }

    /** The scope this instance requires. Exposed so a consumer's tests can pin it. */
    public String requiredWorkloadScope() {
        return requiredWorkloadScope;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = isWorkloadIdentity(jwt)
                ? List.of(new SimpleGrantedAuthority(ROLE_INTERNAL))
                : List.of();
        return new JwtAuthenticationToken(jwt, authorities);
    }

    private boolean isWorkloadIdentity(Jwt jwt) {
        return scopes(jwt).contains(requiredWorkloadScope);
    }

    /**
     * Collect OAuth2 scopes from a JWT. Spring Authorization Server emits {@code scope} as a JSON
     * array; other issuers use a space-delimited {@code scope} string or the {@code scp} array — all
     * three are read so the discriminator is issuer-shape robust. Dropping any one of them turns
     * into a silent 403 on a deployment whose issuer happens to use that shape, which is why all
     * three have their own test.
     */
    private static Set<String> scopes(Jwt jwt) {
        Set<String> scopes = new LinkedHashSet<>();
        Object scope = jwt.getClaim("scope");
        if (scope instanceof Collection<?> collection) {
            for (Object s : collection) {
                if (s != null) scopes.add(s.toString());
            }
        } else if (scope instanceof String s) {
            for (String part : s.split("\\s+")) {
                if (!part.isBlank()) scopes.add(part);
            }
        }
        List<String> scp = jwt.getClaimAsStringList("scp");
        if (scp != null) scopes.addAll(scp);
        return scopes;
    }
}
