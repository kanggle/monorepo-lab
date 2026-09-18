package com.example.security.oauth2;

import java.util.Arrays;
import java.util.Locale;

/**
 * What a gateway does when a token's {@code aud} shares no member with its audience allowlist
 * ({@code platform/contracts/jwt-standard-claims.md} § JWT Validation rule 5).
 *
 * <p>There is deliberately <strong>no default</strong>. A gateway names its mode in its own
 * configuration, and an absent, blank or unknown value fails the boot — the same fail-closed
 * shape as the allowlist itself. A default of {@link #SHADOW} would let a gateway stay in the
 * rollout phase forever without anyone having written that down; a default of {@link #ENFORCE}
 * would turn a forgotten property into a fleet-wide refusal.
 */
public enum AudienceMode {

    /**
     * Rollout phase: a mismatch is <strong>not rejected</strong>. It is logged (jti, the
     * {@code aud} values, the gateway) and counted, and the request proceeds. The contract makes
     * this a bounded device — a gateway leaves it once the measured mismatch count is zero, and
     * that switch is its own change.
     */
    SHADOW,

    /** A mismatch fails validation; the gateway maps it to 403, not the decoder's default 401. */
    ENFORCE;

    /**
     * Parses a configured value, case-insensitively.
     *
     * @throws IllegalArgumentException for a null, blank or unknown value — the caller is bean
     *                                  construction, so this is a startup failure
     */
    public static AudienceMode parse(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException(
                    "audience mode must be configured explicitly (one of "
                            + Arrays.toString(values()) + ") — there is no default");
        }
        String normalized = configured.trim().toUpperCase(Locale.ROOT);
        for (AudienceMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "unknown audience mode '" + configured + "' (expected one of "
                        + Arrays.toString(values()) + ")");
    }
}
