package com.example.fanplatform.membership.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;

/**
 * Authentication converter for the {@code /internal/**} (workload-identity) chain.
 *
 * <p>Grants {@code ROLE_INTERNAL} only to a recognized IAM
 * {@code client_credentials} token — distinguished from an end-user access token
 * by: it carries NO {@code tenant_id} claim AND it carries a machine-client
 * marker ({@code scope}/{@code scp} or {@code client_id}, with {@code sub ==
 * client_id} being the canonical OAuth2 client_credentials shape). An end-user
 * token (tenant-pinned, role/roles claims, no client scope) therefore receives
 * NO authority → the {@code .hasRole("INTERNAL")} gate rejects it with 403. A
 * request with no token never reaches this converter (401 at the entry point).
 *
 * <p>This is the service-side half of the ADR-MONO-005 contract; the FAN-BE-010
 * {@code HttpMembershipChecker} presents the client_credentials Bearer JWT.
 */
public class WorkloadIdentityAuthoritiesConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";
    private static final String CLAIM_TENANT_ID = "tenant_id";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = isWorkloadIdentity(jwt)
                ? List.of(new SimpleGrantedAuthority(ROLE_INTERNAL))
                : List.of();
        return new JwtAuthenticationToken(jwt, authorities);
    }

    private static boolean isWorkloadIdentity(Jwt jwt) {
        // End-user tokens are tenant-pinned; a client_credentials token is not.
        String tenantId = jwt.getClaimAsString(CLAIM_TENANT_ID);
        if (tenantId != null && !tenantId.isBlank()) {
            return false;
        }
        // Machine-client markers emitted by the IAM authorization server for the
        // client_credentials grant.
        boolean hasScope = jwt.hasClaim("scope") || jwt.hasClaim("scp");
        boolean hasClientId = jwt.hasClaim("client_id") || jwt.hasClaim("azp");
        return hasScope || hasClientId;
    }
}
