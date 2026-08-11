package com.example.fanplatform.artist.config;

import com.example.fanplatform.artist.adapter.in.web.security.PublicPaths;
import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.ResourceServerChainAssembler;
import com.example.security.servlet.TenantClaimEnforcer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Service-level Resource Server JWT decoder. Mirrors the fan-platform gateway
 * + community-service so any direct call (gateway bypass) still gets the same
 * validator chain — defense-in-depth (TASK-FAN-BE-003 § Acceptance Criteria
 * "service-level OAuth2 fail-closed re-validation").
 *
 * <h2>ADR-MONO-058 § D4 — the assembly is shared, the policy is not</h2>
 *
 * The {@code NimbusJwtDecoder} construction, the {@code parseCsv} helper and the validator
 * <em>order</em> now come from {@link ResourceServerChainAssembler}. Everything that decides
 * <em>who gets in</em> stays here: the property keys, the issuer allow-list, and the
 * {@link TenantClaimValidator} switches below. The library is called explicitly from this
 * {@code @Configuration}; it registers nothing on its own.
 */
@Configuration
public class ServiceLevelOAuth2Config {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${fanplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${fanplatform.oauth2.required-tenant-id:fan-platform}")
    private String requiredTenantId;

    @Value("${fanplatform.internal.jwt.jwk-set-uri:${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}}")
    private String internalJwkSetUri;

    @Value("${fanplatform.internal.jwt.issuer:${spring.security.oauth2.resourceserver.jwt.issuer-uri}}")
    private String internalIssuer;

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        // Declared as JwtDecoder, not NimbusJwtDecoder: the bean's advertised type is what
        // @ConditionalOnMissingBean(JwtDecoder.class) above and Spring Security's single-decoder
        // resolution in SecurityConfig both key on, and D4 does not change either.
        return decoderAssembly().build();
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        return decoderAssembly().buildValidator();
    }

    /**
     * Workload-identity decoder for {@code /internal/**} (TASK-FAN-BE-045 AC-6,
     * ADR-004 A; ADR-MONO-005). Validates an IAM {@code client_credentials} JWT —
     * issuer + signature + timestamps — and deliberately does NOT pin
     * {@code tenant_id}: routing it through {@link #decoderAssembly()} would give
     * the internal surface a tenant gate it must not have, which is the same
     * reasoning membership-service's copy states.
     *
     * <p><strong>Declared in this class, after {@link #jwtDecoder()}, on purpose.</strong>
     * A second {@code JwtDecoder}-assignable bean in a different {@code @Configuration}
     * would race the {@code @ConditionalOnMissingBean(JwtDecoder.class)} above and
     * could silently suppress the end-user decoder. {@code @Bean} methods within one
     * class are processed top-to-bottom, so the condition always evaluates before
     * this bean exists.
     *
     * <p>Because there are now two decoders, {@code SecurityConfig} pins each chain's
     * decoder explicitly — Spring Security's by-type resolution no longer has a
     * single candidate.
     */
    @Bean
    public NimbusJwtDecoder internalJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(internalJwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(internalIssuer));
        return decoder;
    }

    /**
     * The one place this service's token policy is stated; both beans above are built from it.
     *
     * <p>The assembler installs, in this exact order, the {@code JwtTimestampValidator}, the
     * {@link AllowedIssuersValidator} over the allow-list below, the tenant validator handed to it
     * here, and finally {@code JwtValidators.createDefault()} — the same four the hand-written chain
     * listed, in the same order, because that order decides which {@code OAuth2Error}
     * {@code SecurityConfig}'s entry point sees first and therefore whether a rejection is a 401 or a
     * 403.
     *
     * <p>Private, so it is not a {@code @Bean} method: each caller gets its own (stateless,
     * structurally identical) chain, which is the shape
     * {@link ResourceServerChainAssembler.JwtDecoderBuilder} documents for a service that exposes the
     * decoder and the validator as two separate beans.
     */
    private ResourceServerChainAssembler.JwtDecoderBuilder decoderAssembly() {
        return ResourceServerChainAssembler.jwtDecoder(jwkSetUri)
                .allowedIssuersCsv(allowedIssuersCsv)
                .validator(TenantClaimValidator.forTenant(requiredTenantId)
                        .allowSuperAdminWildcard()   // SUPER_ADMIN platform scope (ADR-MONO-019 § D5)
                        // no .trustEntitledDomains() — fan is outside the entitlement plane
                        .build());
    }

    /**
     * fan's servlet tenant gate — the inner layer behind {@link #jwtTokenValidator()}.
     *
     * <p>An explicit {@code @Bean}, not a component scan: a shared class annotated
     * {@code @Component} would decide this service's policy somewhere nobody looks.
     *
     * <h2>{@code trustEntitledDomains()} is deliberately NOT called</h2>
     *
     * fan sits outside the entitlement plane — none of its four copies ever held an
     * {@code isEntitled} branch (measured: zero, fleet-wide). <strong>This is the first place in
     * the D5 series where a switch stays OFF</strong>, and it is what "every switch defaults
     * closed" was built for: adding {@code .trustEntitledDomains()} here would <em>widen</em>
     * fan's gate to honour a claim it has never honoured, and widening is the quiet direction.
     * The policy pin asserts the refusal, not just the acceptance (ADR-MONO-049 § 1.9,
     * TASK-MONO-387).
     *
     * <h2>{@code /internal/**} is exempt, and that is not a hole</h2>
     *
     * The Order(1) workload chain authenticates with {@link #internalJwtDecoder()}, which does
     * not pin {@code tenant_id} — but it still puts a {@code JwtAuthenticationToken} in the
     * context, so this filter (registered outside the chains) sees it, and a token whose
     * {@code tenant_id} it cannot match is exactly what the gate 401s. Without the exemption
     * every internal call 401s and the follow-target check is dead on arrival — the same
     * prediction membership-service's copy records after testing it (TASK-MONO-387 AC-6). It is
     * not a widening: {@code /internal/**} is not an end-user route, and the chain guarding it
     * requires {@code hasRole("INTERNAL")}, which no end-user token can obtain.
     *
     * <p>Otherwise the exemption is {@code PublicPaths} only — the three actuator probes plus the
     * {@code /actuator/health/} subtree. <strong>community's copy reasoned about this axis
     * explicitly and refused the blanket {@code /actuator/} prefix that scm's services shipped</strong>
     * ("a blanket prefix would bypass the tenant gate for endpoints that may be added later …
     * we want a fail-closed posture there"). That judgement is preserved here, where it now
     * holds for the whole project rather than for whoever happened to read that one file.
     */
    /** The prefix the workload-identity chain owns — kept next to the exemption that needs it. */
    private static final String INTERNAL_PREFIX = "/internal/";

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
}
