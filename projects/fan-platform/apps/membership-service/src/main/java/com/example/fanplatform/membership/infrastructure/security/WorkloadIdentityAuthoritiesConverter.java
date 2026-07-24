package com.example.fanplatform.membership.infrastructure.security;

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
 * Authentication converter for the {@code /internal/**} (workload-identity) chain.
 *
 * <p>Grants {@code ROLE_INTERNAL} to a recognized IAM {@code client_credentials}
 * token via a <b>positive</b> discriminator: the token must carry the required
 * workload <b>scope</b> ({@link #REQUIRED_WORKLOAD_SCOPE}). This is the axis the
 * platform sanctions for machine tokens — {@code platform/security-rules.md}
 * ("internal-only surface MUST require a claim only a machine can carry —
 * exactly one of: subject allow-list OR required scope") and
 * {@code platform/contracts/jwt-standard-claims.md} ("machine
 * ({@code client_credentials}) tokens authorize on the {@code scope} axis").
 * IAM grants {@code community-service-client} the {@code membership.read} scope
 * for exactly this internal surface (V0009); an end-user token carries user
 * scopes ({@code openid}/{@code profile}/{@code email}/{@code tenant.read}) and
 * so never matches → the {@code .hasRole("INTERNAL")} gate rejects it with 403.
 * A request with no token never reaches this converter (401 at the entry point).
 *
 * <p><b>Do NOT gate on {@code tenant_id} absence.</b> Every IAM token — including
 * the {@code client_credentials} grant — carries {@code tenant_id}
 * (jwt-standard-claims.md: "issued on every grant; a token without it is rejected
 * at the edge"); the IAM {@code TenantClaimTokenCustomizer} stamps it fail-closed
 * and cannot suppress it. The earlier "reject if {@code tenant_id} present" check
 * was an unsanctioned negative discriminator that rejected the real tenant-scoped
 * IAM cc token → 403 for every membership access-check (TASK-FAN-BE-029). It only
 * shipped green because the test helper minted a fabricated {@code tenant_id}-less
 * token. Mirrors the working ecommerce order-service precedent
 * ({@code SystemClientSubjectValidator}), which authorizes cc tokens on a positive
 * marker and is {@code tenant_id}-agnostic.
 *
 * <p>This is the service-side half of the ADR-MONO-005 contract; the FAN-BE-010
 * {@code HttpMembershipChecker} presents the client_credentials Bearer JWT.
 */
public class WorkloadIdentityAuthoritiesConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    /**
     * The workload scope IAM grants {@code community-service-client} for the
     * {@code /internal/membership/**} surface (seed migration V0009). Only a
     * machine token issued for that client carries it.
     */
    public static final String REQUIRED_WORKLOAD_SCOPE = "membership.read";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = isWorkloadIdentity(jwt)
                ? List.of(new SimpleGrantedAuthority(ROLE_INTERNAL))
                : List.of();
        return new JwtAuthenticationToken(jwt, authorities);
    }

    private static boolean isWorkloadIdentity(Jwt jwt) {
        return scopes(jwt).contains(REQUIRED_WORKLOAD_SCOPE);
    }

    /**
     * Collect OAuth2 scopes from a JWT. SAS emits {@code scope} as a JSON array;
     * other issuers use a space-delimited {@code scope} string or the {@code scp}
     * array — all three are handled so the discriminator is issuer-shape robust.
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
