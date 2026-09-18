package com.example.security.oauth2;

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
 * The edge audience check — {@code platform/contracts/jwt-standard-claims.md} § JWT
 * Validation rule 5.
 *
 * <p><strong>Rule:</strong> admit iff the token's {@code aud} values intersect this edge's
 * allowlist of client ids. {@code aud} names the registered client that obtained the token (the
 * identity-platform's issuance default — not a platform name). A single-string {@code aud} is a
 * one-element set; a token with no {@code aud} is the empty set and never intersects.
 *
 * <h2>Why it lives here and not in {@code java-gateway}</h2>
 *
 * It was written for the six reactive gateways and lived beside them until {@code TASK-MONO-712}.
 * Rule 5 binds every <em>edge</em> — a <strong>position</strong> (the first validation of a token
 * presented from outside the trust boundary), not a service type — and {@code platform-console}'s
 * {@code console-bff} is one while being a <strong>servlet</strong> service. A servlet service may
 * not consume {@code libs/java-gateway} (ADR-MONO-049 § D1 — it would drag WebFlux and Spring Cloud
 * Gateway onto its runtime classpath), so this class had to move or be copied. It moved: the same
 * resolution, for the same reason, that {@link TenantClaimValidator} already went through
 * (ADR-MONO-049 § D5-1). It is framework-neutral — an {@link OAuth2TokenValidator} plus a
 * Micrometer counter, nothing servlet, nothing reactive — and {@code java-gateway} depends on
 * {@code java-security}, so the six gateways reach it unchanged while
 * {@code GatewayErrorCodes.AUDIENCE_MISMATCH} points at the constant below rather than restating it.
 *
 * <h2>Fail-closed construction</h2>
 *
 * An empty allowlist throws, exactly like {@link AllowedIssuersValidator}: "no audiences
 * configured" must never degrade into "accept any audience". Construction happens inside an edge's
 * decoder bean, so this is a startup failure. <strong>That is the whole reason this class exists
 * rather than the framework property</strong>: Spring Boot's
 * {@code spring.security.oauth2.resourceserver.jwt.audiences} adds no validator at all when the
 * list is absent or empty, so the check disappears silently instead of failing the boot
 * (measured in {@code TASK-MONO-698} § AC-0 (d); pinned by {@code TASK-MONO-712} AC-1).
 *
 * <h2>Two modes</h2>
 *
 * <ul>
 *   <li>{@link AudienceMode#SHADOW} — a mismatch returns <em>success</em>, after a WARN log line
 *       ({@code gateway}, {@code jti}, the {@code aud} values) and a counter increment. This is
 *       the measurement phase: the contract's switch to rejection is conditioned on the measured
 *       mismatch count being zero.</li>
 *   <li>{@link AudienceMode#ENFORCE} — a mismatch returns the {@link #ERROR_CODE_AUDIENCE_MISMATCH}
 *       error. The edge's entry point finds that code in the exception cause chain and answers
 *       403, not the decoder's default 401: re-authenticating cannot change the issuing client.</li>
 * </ul>
 *
 * <p>{@code console-bff} ships {@link AudienceMode#ENFORCE} with no shadow phase
 * ({@code TASK-MONO-712} AC-3 — the owner's decision E). Shadow is not skipped for convenience
 * there: its purpose is to discover an unmeasured caller population, and that population was
 * measured instead ({@code TASK-MONO-712} § AC-0).
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
 * <p>🔴 The metric name and both tag names are <strong>wire</strong>, not decoration:
 * {@code TASK-MONO-697} § AC-0 reads {@code gateway_jwt_audience_total{gateway=…,outcome=…}} to
 * decide whether the six gateways may leave shadow. Renaming either here would leave that query
 * returning nothing — which reads as "zero mismatches", the exact false green that AC-0's
 * denominator rule exists to catch. They kept the name {@code gateway} through this move for that
 * reason, even though the class now also serves a non-gateway edge.
 *
 * <p>{@code GatewayJwtDecoders.validatorChain} (in {@code libs/java-gateway}) takes an instance of
 * <em>this class</em> as a required argument — not any {@code OAuth2TokenValidator} — so a gateway
 * cannot hand it a no-op, and runs it <em>after</em> the rest of the chain has passed (see there
 * for why). console-bff's decoder bean holds the same ordering for the same reason.
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
