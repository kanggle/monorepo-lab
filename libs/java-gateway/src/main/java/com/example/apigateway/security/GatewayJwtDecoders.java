package com.example.apigateway.security;

import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Assembles a gateway's reactive JWT decoder and its validator chain.
 *
 * <p>This is the part of {@code OAuth2ResourceServerConfig} that was identical in wms, scm
 * and fan — and it is the part worth de-duplicating, because <strong>it is the validator
 * chain</strong>. Three hand-maintained copies of "which checks run, and in what order"
 * is the same mechanism that lost the {@code FailOpenRateLimiter} fix (ADR-MONO-048 § 1.3):
 * nothing would have flagged a fourth gateway that quietly dropped the issuer check.
 *
 * <h2>Why the property binding stayed behind</h2>
 *
 * ADR-MONO-048 § D3 lists this class's parameter as "property prefix", with
 * {@code @Value → @ConfigurationProperties} as the sketched mechanism. That mechanism is
 * <strong>not</strong> used, deliberately:
 *
 * <ul>
 *   <li>The three prefixes genuinely differ ({@code wms.} / {@code scmplatform.} /
 *       {@code fanplatform.}), so a single {@code @ConfigurationProperties} class cannot
 *       serve them without a per-domain subclass — a class per domain to avoid a class per
 *       domain.</li>
 *   <li>More importantly it would move the failure mode. {@code @Value} on a placeholder
 *       with no default <strong>fails the context</strong> when the property is absent;
 *       {@code @ConfigurationProperties} binds null and carries on. In a security config,
 *       "carries on" means an empty issuer allowlist. (Today that still fails closed —
 *       {@link AllowedIssuersValidator} rejects an empty list — but the guard would be the
 *       only thing left standing, and rewriting a fail-fast into a fail-fast-by-luck is not
 *       a refactor.)</li>
 * </ul>
 *
 * So each domain keeps its own {@code @Value}s, with its property keys unchanged to the
 * byte, and hands the values here. The prefix <em>is</em> the parameter, expressed where it
 * belongs.
 */
public final class GatewayJwtDecoders {

    private GatewayJwtDecoders() {}

    /**
     * The standard gateway validator chain, in order: token timestamps → issuer allowlist →
     * the domain's tenant gate → Spring's defaults.
     *
     * @param allowedIssuers non-empty; {@link AllowedIssuersValidator} rejects an empty list
     *                       rather than degrading to "accept any issuer"
     * @param tenantGate     the domain's {@link TenantClaimValidator} — its policy, its call
     */
    public static OAuth2TokenValidator<Jwt> validatorChain(
            List<String> allowedIssuers, OAuth2TokenValidator<Jwt> tenantGate) {
        Objects.requireNonNull(tenantGate, "tenantGate");
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new AllowedIssuersValidator(allowedIssuers));
        validators.add(tenantGate);
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /** A JWKS-backed reactive decoder wired to {@code validator}. */
    public static ReactiveJwtDecoder nimbus(String jwkSetUri, OAuth2TokenValidator<Jwt> validator) {
        Objects.requireNonNull(jwkSetUri, "jwkSetUri");
        Objects.requireNonNull(validator, "validator");
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** Splits a comma-separated property value, trimming and dropping empties. */
    public static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) {
            return out;
        }
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
