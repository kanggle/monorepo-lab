package com.kanggle.platformconsole.bff.infrastructure.security;

import com.example.security.oauth2.AllowedAudiencesValidator;
import com.example.security.oauth2.AudienceMode;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

/**
 * The rule-5 audience check at the console-bff edge — {@code TASK-MONO-712}, executing the
 * owner's decision <strong>E</strong> recorded in {@code TASK-MONO-698} § AC-3.
 *
 * <h2>Why this bean exists at all</h2>
 *
 * Spring Boot can do an audience check from configuration alone
 * ({@code spring.security.oauth2.resourceserver.jwt.audiences}), and for a while that looked like
 * the whole job — <em>"one property line"</em>. It is <strong>half true</strong>, and the false
 * half is the dangerous one. Boot adds the audience validator <em>only if the list is non-empty</em>:
 * with the property absent, blank, or emptied by a bad environment override, no validator is
 * registered and <strong>the check disappears without a sound</strong> — the application boots,
 * every request passes, and nothing anywhere is red.
 *
 * <p>{@code jwt-standard-claims.md} § JWT Validation rule 5 does not ask for "the check is on". It
 * asks for a check that <em>cannot be off</em>: an absent or empty allowlist is a
 * <strong>startup failure</strong>. A property cannot express that. {@link AllowedAudiencesValidator}
 * can, because emptiness throws in its constructor — and this bean is where that constructor runs.
 * {@code InboundAudienceShippedConfigTest} is the executable form of that claim: delete the
 * shipped value and the context fails to start.
 *
 * <h2>What is admitted</h2>
 *
 * Exactly one client id — {@code platform-console-web} — and that is a measurement, not a guess
 * ({@code TASK-MONO-712} § AC-0). Both tokens that can arrive here carry it:
 *
 * <ul>
 *   <li>the <strong>base</strong> login token ({@code authorization_code}) — pinned by
 *       {@code FormLoginIntegrationTest};</li>
 *   <li>the <strong>assumed</strong> token minted by the assume-tenant exchange when an operator
 *       switches tenant, which is what console-web actually forwards most of the time — pinned by
 *       {@code AssumeTenantExchangeIntegrationTest}.</li>
 * </ul>
 *
 * <p>🔴 The assume-tenant exchange sends {@code audience=<selected tenant>} in its RFC 8693
 * <em>request</em>. That parameter is this repository's <strong>tenant selector</strong>; it does
 * not become the token's {@code aud} (the provider builds the token context from the registered
 * client). Reading the parameter name as the claim and putting tenant ids on this allowlist would
 * have made every tenant-switched session 403.
 *
 * <p>🔴 {@code "console-bff"} — the service's own name — is deliberately <strong>not</strong>
 * admitted, even though seven integration tests minted it before {@code TASK-MONO-712} AC-4. No
 * production token can ever carry it, so admitting it would promote a service name into a real
 * credential value ({@code TASK-MONO-696} AC-5 forbids exactly this; {@code TASK-MONO-714} exists
 * to delete the same mistake in ecommerce's order-service).
 *
 * <h2>No shadow phase</h2>
 *
 * The six gateways ship {@link AudienceMode#SHADOW} while their caller population is being
 * measured. console-bff ships {@link AudienceMode#ENFORCE} directly (decision E, item 2). That is
 * not a shortcut: shadow exists to discover an unmeasured population, and this edge's population
 * was measured instead — it has exactly one caller (console-web's four server-side routes), which
 * § AC-0 established from configuration and CI-authoritative assertions rather than from traffic.
 * Boot's property could not have shadowed anyway: it has no mode, no metric and no log
 * ({@code TASK-MONO-698} § AC-1 (d)).
 */
@Configuration
public class InboundAudienceConfig {

    /** The metric/log name this edge reports under. Not request data — a constant of the edge. */
    static final String EDGE_NAME = "console-bff";

    private final String jwkSetUri;
    private final String issuerUri;
    private final List<String> allowedAudiences;
    private final MeterRegistry meterRegistry;

    public InboundAudienceConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
            @Value("${console-bff.security.allowed-audiences:}") List<String> allowedAudiences,
            MeterRegistry meterRegistry) {
        this.jwkSetUri = jwkSetUri;
        this.issuerUri = issuerUri;
        this.allowedAudiences = allowedAudiences;
        this.meterRegistry = meterRegistry;
    }

    /**
     * The rule-5 validator for this edge.
     *
     * <p>Constructed as a bean rather than inline so a test can assert the <em>shipped</em>
     * allowlist without booting a web server, and so the fail-closed constructor runs during
     * context refresh — an empty allowlist is a startup failure, which is the property Boot's
     * own audience support cannot provide.
     */
    @Bean
    public AllowedAudiencesValidator consoleBffAudienceValidator() {
        return new AllowedAudiencesValidator(
                EDGE_NAME, allowedAudiences, AudienceMode.ENFORCE, meterRegistry);
    }

    /**
     * Replaces Boot's auto-configured decoder so the audience validator joins the chain.
     *
     * <p>The rest of the chain is unchanged from what the auto-configuration built:
     * {@link JwtValidators#createDefaultWithIssuer} keeps signature, {@code exp}/{@code nbf} and
     * {@code iss}. The audience check runs <strong>last</strong>, mirroring
     * {@code GatewayJwtDecoders.validatorChain}: a token that fails signature or issuer is not a
     * "wrong client" — reporting it as one would hand an attacker a 403/401 oracle and would put
     * garbage {@code aud} values into the log line.
     */
    @Bean
    public JwtDecoder jwtDecoder(AllowedAudiencesValidator audienceValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> chain = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator);
        decoder.setJwtValidator(chain);
        return decoder;
    }
}
