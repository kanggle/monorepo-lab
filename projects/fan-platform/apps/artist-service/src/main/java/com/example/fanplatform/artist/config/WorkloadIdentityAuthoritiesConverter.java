package com.example.fanplatform.artist.config;

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
 * Authentication converter for artist-service's {@code /internal/**}
 * (workload-identity) chain — TASK-FAN-BE-045 AC-6, ADR-004 A, ADR-MONO-005.
 *
 * <p>Grants {@code ROLE_INTERNAL} on a <b>positive</b> discriminator: the token
 * must carry {@link #REQUIRED_WORKLOAD_SCOPE}. That is the axis
 * {@code platform/security-rules.md} sanctions for machine tokens ("exactly one
 * of: subject allow-list OR required scope"). A request with no token never
 * reaches this converter (401 at the entry point); a token without the scope
 * gets no authority and the {@code hasRole("INTERNAL")} gate answers 403.
 *
 * <h2>Why the scope is {@code artist.read} and NOT {@code fan-platform.artist.read}</h2>
 *
 * Those are two different scopes in two different families, and picking the wrong
 * one would make this discriminator discriminate nothing:
 *
 * <ul>
 *   <li>{@code fan-platform.artist.read} is an <b>end-user resource scope</b>. IAM
 *       migration {@code V0030} grants it to the fan web client, and the demo seed
 *       requests it on the ordinary user token. If this surface keyed on it, every
 *       logged-in fan would hold {@code ROLE_INTERNAL} and could call the internal
 *       endpoint directly.</li>
 *   <li>{@code artist.read} joins the <b>machine scope</b> family
 *       ({@code account.read}, {@code membership.read}) that IAM grants only to
 *       {@code client_credentials} clients. Only a machine token carries it.</li>
 * </ul>
 *
 * <p><b>Do NOT gate on {@code tenant_id} absence.</b> Every IAM token — including
 * the {@code client_credentials} grant — carries {@code tenant_id}
 * ({@code platform/contracts/jwt-standard-claims.md}); a "reject if tenant_id
 * present" check is an unsanctioned negative discriminator that rejects the real
 * token and only ever ships green against a fabricated one (TASK-FAN-BE-029).
 *
 * <h2>Second instance of this shape</h2>
 *
 * membership-service carries a structurally identical converter. Two copies make
 * this a promotion candidate for {@code libs/java-security} — but that is a
 * shared-path change and needs its own root task, so it is named here rather than
 * done here (see the task's follow-up note).
 */
public class WorkloadIdentityAuthoritiesConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String ROLE_INTERNAL = "ROLE_INTERNAL";

    /**
     * The workload scope IAM grants {@code community-service-client} for this
     * service's {@code /internal/artists/**} surface. Machine-only by
     * construction — see the class Javadoc on why the {@code fan-platform.*}
     * resource scope cannot be used here.
     */
    public static final String REQUIRED_WORKLOAD_SCOPE = "artist.read";

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
