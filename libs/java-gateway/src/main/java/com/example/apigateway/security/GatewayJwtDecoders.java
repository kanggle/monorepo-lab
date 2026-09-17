package com.example.apigateway.security;

import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
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
     * The standard gateway validator chain: token timestamps → issuer allowlist → the domain's
     * tenant gate → Spring's defaults, and — <strong>only once all of those pass</strong> — the
     * audience allowlist ({@code jwt-standard-claims.md} § JWT Validation rule 5).
     *
     * <p><strong>The audience gate is a required parameter of a concrete type</strong>
     * (TASK-MONO-696). The contract said "gateways reject a mismatched {@code aud}" for as long as
     * it has existed, and no gateway did: a property configured an auto-configured decoder that
     * every gateway replaces with its own. A check that lives in each gateway's wiring is a check
     * a gateway can forget; one that is an argument of the only chain there is cannot be.
     *
     * <p><strong>Why the audience check runs last, and only on an otherwise-valid token.</strong>
     * {@link DelegatingOAuth2TokenValidator} runs every delegate and collects every error. Were the
     * audience gate simply another delegate, then (a) in shadow mode, expired, forged-issuer and
     * cross-tenant tokens — already refused — would be counted as audience mismatches and inflate
     * the very number the switch to rejection is conditioned on; and (b) in enforce mode, a token
     * failing both issuer and audience would carry both errors and the entry point's 403 mapping
     * would outrank the 401 that rule 4 (issuer) owes it. Sequencing keeps the rule order of the
     * contract: an authentication failure stays 401, a cross-tenant token stays
     * {@code TENANT_FORBIDDEN}, and the audience outcome is measured only on tokens that would
     * otherwise have been admitted.
     *
     * @param allowedIssuers non-empty; {@link AllowedIssuersValidator} rejects an empty list
     *                       rather than degrading to "accept any issuer"
     * @param audienceGate   this gateway's audience allowlist and mode — required
     * @param tenantGate     the domain's {@link TenantClaimValidator} — its policy, its call
     */
    public static OAuth2TokenValidator<Jwt> validatorChain(
            List<String> allowedIssuers,
            AllowedAudiencesValidator audienceGate,
            OAuth2TokenValidator<Jwt> tenantGate) {
        Objects.requireNonNull(audienceGate, "audienceGate");
        Objects.requireNonNull(tenantGate, "tenantGate");
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new AllowedIssuersValidator(allowedIssuers));
        validators.add(tenantGate);
        validators.add(JwtValidators.createDefault());
        return new AudienceCheckedChain(new DelegatingOAuth2TokenValidator<>(validators), audienceGate);
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

    /**
     * The base chain, then the audience gate if and only if the base chain passed. See
     * {@link #validatorChain} for why the two are sequenced rather than delegated side by side.
     */
    public static final class AudienceCheckedChain implements OAuth2TokenValidator<Jwt> {

        private final DelegatingOAuth2TokenValidator<Jwt> base;
        private final AllowedAudiencesValidator audienceGate;

        AudienceCheckedChain(DelegatingOAuth2TokenValidator<Jwt> base,
                             AllowedAudiencesValidator audienceGate) {
            this.base = base;
            this.audienceGate = audienceGate;
        }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            OAuth2TokenValidatorResult result = base.validate(token);
            if (result.hasErrors()) {
                return result;
            }
            return audienceGate.validate(token);
        }

        public DelegatingOAuth2TokenValidator<Jwt> base() {
            return base;
        }

        public AllowedAudiencesValidator audienceGate() {
            return audienceGate;
        }
    }
}
