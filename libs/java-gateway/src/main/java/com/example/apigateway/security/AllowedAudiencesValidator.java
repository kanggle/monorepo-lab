package com.example.apigateway.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The gateway audience check — {@code platform/contracts/jwt-standard-claims.md} § JWT
 * Validation rule 5.
 *
 * <p><strong>Rule:</strong> admit iff the token's {@code aud} values intersect this gateway's
 * allowlist of client ids. {@code aud} names the registered client that obtained the token (the
 * identity-platform's issuance default — not a platform name). A single-string {@code aud} is a
 * one-element set; a token with no {@code aud} is the empty set and never intersects.
 *
 * <h2>Fail-closed construction</h2>
 *
 * An empty allowlist throws, exactly like {@code AllowedIssuersValidator}: "no audiences
 * configured" must never degrade into "accept any audience". Construction happens inside a
 * gateway's decoder bean, so this is a startup failure.
 *
 * <h2>Two modes</h2>
 *
 * <ul>
 *   <li>{@link AudienceMode#SHADOW} — a mismatch returns <em>success</em>, after a WARN log line
 *       ({@code gateway}, {@code jti}, the {@code aud} values) and a counter increment. This is
 *       the measurement phase: the contract's switch to rejection is conditioned on the measured
 *       mismatch count being zero.</li>
 *   <li>{@link AudienceMode#ENFORCE} — a mismatch returns the {@link #ERROR_CODE_AUDIENCE_MISMATCH}
 *       error. The gateway's entry point finds that code in the exception cause chain and answers
 *       403, not the decoder's default 401: re-authenticating cannot change the issuing client.</li>
 * </ul>
 *
 * <h2>The metric</h2>
 *
 * {@value #METRIC_NAME} with exactly two tags — {@value #TAG_GATEWAY} and {@value #TAG_OUTCOME}
 * ({@value #OUTCOME_MATCH} / {@value #OUTCOME_MISMATCH_SHADOWED} / {@value #OUTCOME_MISMATCH_REJECTED}).
 * <strong>The {@code aud} values are never a tag</strong>: they come from the token, so tagging
 * with them hands series cardinality to whoever can get a client registered. They go in the log
 * line instead. Matches are counted too, because "zero mismatches" over zero checked requests is
 * not a measurement — the phase-2 switch needs both numbers.
 *
 * <p>{@link GatewayJwtDecoders#validatorChain} takes an instance of <em>this class</em> as a
 * required argument — not any {@code OAuth2TokenValidator} — so a gateway cannot hand it a
 * no-op, and runs it <em>after</em> the rest of the chain has passed (see there for why).
 */
public final class AllowedAudiencesValidator implements OAuth2TokenValidator<Jwt> {

    /** The OAuth2 error code a rejected audience carries. The entry points map it to 403. */
    public static final String ERROR_CODE_AUDIENCE_MISMATCH = "audience_mismatch";

    public static final String METRIC_NAME = "gateway.jwt.audience";
    public static final String TAG_GATEWAY = "gateway";
    public static final String TAG_OUTCOME = "outcome";
    public static final String OUTCOME_MATCH = "match";
    public static final String OUTCOME_MISMATCH_SHADOWED = "mismatch_shadowed";
    public static final String OUTCOME_MISMATCH_REJECTED = "mismatch_rejected";

    private static final Logger log = LoggerFactory.getLogger(AllowedAudiencesValidator.class);

    /** Log-hygiene bound for a single {@code aud} value; client ids are far shorter. */
    private static final int MAX_LOGGED_VALUE_LENGTH = 128;

    private final String gateway;
    private final Set<String> allowedAudiences;
    private final AudienceMode mode;
    private final Counter matchCounter;
    private final Counter mismatchCounter;

    /**
     * @param gateway          a short, fixed name for this edge — it becomes a metric tag and a log
     *                         field, so it must be a constant of the gateway, never request data
     * @param allowedAudiences the client ids admitted at this edge; must contain at least one
     *                         non-blank value
     * @param mode             see {@link AudienceMode}
     * @param meterRegistry    where the outcome counter is registered
     * @throws IllegalArgumentException if {@code gateway} is blank or {@code allowedAudiences} has
     *                                  no non-blank value
     */
    public AllowedAudiencesValidator(String gateway, List<String> allowedAudiences,
                                     AudienceMode mode, MeterRegistry meterRegistry) {
        if (gateway == null || gateway.isBlank()) {
            throw new IllegalArgumentException("gateway must be a non-blank constant name");
        }
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        Set<String> allowed = new LinkedHashSet<>();
        if (allowedAudiences != null) {
            for (String value : allowedAudiences) {
                if (value != null && !value.isBlank()) {
                    allowed.add(value.trim());
                }
            }
        }
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException(
                    "allowedAudiences must not be empty for gateway '" + gateway + "' — an edge "
                            + "with no audience allowlist would admit any client; configure the "
                            + "client ids measured to reach this gateway");
        }
        this.gateway = gateway;
        this.allowedAudiences = Set.copyOf(allowed);
        this.mode = mode;
        this.matchCounter = Counter.builder(METRIC_NAME)
                .description("Gateway JWT audience checks by outcome (jwt-standard-claims rule 5)")
                .tag(TAG_GATEWAY, gateway)
                .tag(TAG_OUTCOME, OUTCOME_MATCH)
                .register(meterRegistry);
        this.mismatchCounter = Counter.builder(METRIC_NAME)
                .description("Gateway JWT audience checks by outcome (jwt-standard-claims rule 5)")
                .tag(TAG_GATEWAY, gateway)
                .tag(TAG_OUTCOME, mode == AudienceMode.SHADOW
                        ? OUTCOME_MISMATCH_SHADOWED
                        : OUTCOME_MISMATCH_REJECTED)
                .register(meterRegistry);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audiences = token.getAudience();
        if (audiences != null) {
            for (String audience : audiences) {
                if (audience != null && allowedAudiences.contains(audience)) {
                    matchCounter.increment();
                    return OAuth2TokenValidatorResult.success();
                }
            }
        }
        mismatchCounter.increment();
        log.warn("JWT audience not on allowlist: gateway={} mode={} jti={} aud={}",
                gateway, mode, sanitize(token.getId()), sanitize(audiences));
        if (mode == AudienceMode.SHADOW) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                ERROR_CODE_AUDIENCE_MISMATCH,
                "The client this token was issued to is not admitted at this gateway",
                null));
    }

    /** The gateway name this validator reports under. */
    public String gateway() {
        return gateway;
    }

    /** The configured mode. */
    public AudienceMode mode() {
        return mode;
    }

    /** The effective allowlist (trimmed, blanks dropped). */
    public Set<String> allowedAudiences() {
        return allowedAudiences;
    }

    private static List<String> sanitize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(values.size());
        for (String value : values) {
            out.add(sanitize(value));
        }
        return out;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String flattened = value.replaceAll("[\\r\\n\\t]", "_");
        return flattened.length() > MAX_LOGGED_VALUE_LENGTH
                ? flattened.substring(0, MAX_LOGGED_VALUE_LENGTH) + "…"
                : flattened;
    }
}
