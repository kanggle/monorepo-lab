package com.example.fanplatform.membership.infrastructure.security;

import com.example.fanplatform.membership.presentation.security.PublicPaths;
import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.TenantClaimEnforcer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the two JWT decoders used by membership-service:
 *
 * <ul>
 *   <li><b>endUserJwtDecoder</b> — the tenant-pinned end-user decoder
 *       ({@code AllowedIssuersValidator} + {@code TenantClaimValidator}) for
 *       {@code /api/fan/**}. Defense-in-depth: a gateway-bypassing call still
 *       gets the same validator chain.</li>
 *   <li><b>internalJwtDecoder</b> — the workload-identity decoder for
 *       {@code /internal/**}. Validates an IAM {@code client_credentials} JWT
 *       (issuer + signature + timestamps); {@code tenant_id} is intentionally NOT
 *       pinned (client_credentials tokens carry no tenant claim). Built from the
 *       JWKS URI directly so startup is not coupled to auth-service availability
 *       (JWKS fetched lazily on first verification). ADR-MONO-005.</li>
 * </ul>
 *
 * <p>The two are named beans (NOT {@code @Primary}) — each {@code SecurityFilterChain}
 * in {@link SecurityConfig} wires its decoder explicitly so Spring Boot's single
 * default {@code JwtDecoder} auto-configuration does not collide.
 */
@Configuration
public class ServiceLevelOAuth2Config {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String endUserJwkSetUri;

    @Value("${fanplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${fanplatform.oauth2.required-tenant-id:fan-platform}")
    private String requiredTenantId;

    @Value("${fanplatform.internal.jwt.jwk-set-uri}")
    private String internalJwkSetUri;

    @Value("${fanplatform.internal.jwt.issuer}")
    private String internalIssuer;

    @Bean
    public NimbusJwtDecoder endUserJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(endUserJwkSetUri).build();
        decoder.setJwtValidator(endUserTokenValidator());
        return decoder;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> endUserTokenValidator() {
        List<String> allowedIssuers = parseCsv(allowedIssuersCsv);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new AllowedIssuersValidator(allowedIssuers));
        validators.add(TenantClaimValidator.forTenant(requiredTenantId)
                .allowSuperAdminWildcard()   // SUPER_ADMIN platform scope (ADR-MONO-019 § D5)
                // no .trustEntitledDomains() — fan is outside the entitlement plane
                .build());
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * Workload-identity decoder for {@code /internal/**}. Pins the IAM issuer +
     * default timestamp checks; does NOT pin tenant_id.
     */
    @Bean
    public NimbusJwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(internalJwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(internalIssuer));
        return decoder;
    }

    /**
     * The prefix the workload-identity chain owns. Kept here, next to the exemption that needs
     * it, rather than inside the filter — the filter is shared now.
     */
    private static final String INTERNAL_PREFIX = "/internal/";

    /**
     * fan's servlet tenant gate — the inner layer behind {@link #endUserTokenValidator()}.
     *
     * <h2>{@code trustEntitledDomains()} is deliberately NOT called</h2>
     *
     * fan sits outside the entitlement plane — none of its four copies ever held an
     * {@code isEntitled} branch (measured: zero). <strong>This is the first place in the D5
     * series where a switch stays OFF.</strong> Adding {@code .trustEntitledDomains()} would
     * <em>widen</em> fan's gate to honour a claim it has never honoured, and widening is the
     * quiet direction. The policy pin asserts the refusal, not just the acceptance.
     *
     * <h2>Why {@code /internal/**} is exempt, and why that is not a hole</h2>
     *
     * membership runs <strong>two</strong> filter chains: an {@code @Order(1)}
     * {@code securityMatcher("/internal/**")} workload-identity chain, and the end-user chain.
     * The internal chain authenticates with {@link #internalJwtDecoder()}, whose Javadoc says it
     * plainly — <em>"does NOT pin tenant_id"</em>. It still puts a {@code JwtAuthenticationToken}
     * in the context, so this filter (registered outside the chains, at
     * {@code LOWEST_PRECEDENCE-100}) sees it — and a token with no {@code tenant_id} is exactly
     * what the gate 401s.
     *
     * <p><strong>Without the exemption, every internal call 401s</strong> and
     * {@code community/HttpMembershipChecker} stops working. That is not an argument, it is a
     * prediction, and {@code TASK-MONO-387} AC-6 tested it by removing the clause and running
     * the suite. It went red. The exemption is load-bearing; it stays.
     *
     * <p>It is <em>not</em> a hole in the tenant gate: {@code /internal/**} is not an end-user
     * route, it is workload identity, and the chain that guards it requires
     * {@code hasRole("INTERNAL")}. A tenant claim is not the thing standing between a caller and
     * that surface.
     */
    @Bean
    public TenantClaimEnforcer tenantClaimEnforcer() {
        return TenantClaimEnforcer.forTenant(requiredTenantId)
                .exempt(request -> PublicPaths.isPublic(request)
                        || (request.getRequestURI() != null
                                && request.getRequestURI().startsWith(INTERNAL_PREFIX)))
                .allowSuperAdminWildcard()
                // no .trustEntitledDomains() — see above
                .build();
    }

    private static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
