package com.example.apigateway.filter;

import java.util.Objects;
import java.util.function.Function;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * One "header ← claim" rule for {@link JwtHeaderEnrichmentFilter}.
 *
 * <p>The three gateways inject overlapping-but-different header sets. Expressing that as a
 * list of typed mappings — rather than as a bag of booleans on a config object — keeps two
 * properties that matter:
 *
 * <ul>
 *   <li><strong>Domain logic stays in the domain.</strong> The extractor is a
 *       {@code Function<Jwt,String>}, so scm's {@code X-Token-Type} heuristic (is this a
 *       client_credentials token?) lives in scm. The library never learns what a
 *       client_credentials token is, and no {@code libs/} class grows a branch that only
 *       one consumer takes.</li>
 *   <li><strong>It does not become a configuration language.</strong> ADR-MONO-048 names
 *       that as a negative outcome to avoid: parameters that leak into yml until a
 *       domain's edge policy is no longer readable in its code. A mapping list is code, at
 *       the wiring site, and reads as the policy it is.</li>
 * </ul>
 *
 * <p>{@link Presence} exists because the domains were not uniform about it and the
 * difference is observable: {@code sub}/{@code email} were written whenever
 * <em>non-null</em>, {@code tenant_id}/{@code scope} only when <em>non-blank</em>, and
 * roles <em>always</em>. Collapsing the three into one rule would be a behaviour change,
 * so all three survive (ADR-MONO-048 § D6).
 */
public record JwtHeaderMapping(String header, Function<Jwt, String> extractor, Presence presence) {

    public enum Presence {
        /** Write the header even when the resolved value is null or blank. */
        ALWAYS,
        /** Write only when the resolved value is non-null (a blank value is still written). */
        SKIP_IF_NULL,
        /** Write only when the resolved value is non-null and not blank. */
        SKIP_IF_BLANK
    }

    public JwtHeaderMapping {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(extractor, "extractor");
        Objects.requireNonNull(presence, "presence");
    }

    public static JwtHeaderMapping always(String header, Function<Jwt, String> extractor) {
        return new JwtHeaderMapping(header, extractor, Presence.ALWAYS);
    }

    public static JwtHeaderMapping skipIfNull(String header, Function<Jwt, String> extractor) {
        return new JwtHeaderMapping(header, extractor, Presence.SKIP_IF_NULL);
    }

    public static JwtHeaderMapping skipIfBlank(String header, Function<Jwt, String> extractor) {
        return new JwtHeaderMapping(header, extractor, Presence.SKIP_IF_BLANK);
    }

    /** Resolves the claim and decides whether this mapping writes anything. */
    boolean writes(String resolved) {
        return switch (presence) {
            case ALWAYS -> true;
            case SKIP_IF_NULL -> resolved != null;
            case SKIP_IF_BLANK -> resolved != null && !resolved.isBlank();
        };
    }
}
