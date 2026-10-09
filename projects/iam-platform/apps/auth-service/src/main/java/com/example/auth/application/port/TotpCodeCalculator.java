package com.example.auth.application.port;

/**
 * TASK-MONO-771 S2b — the RFC 6238 (TOTP) computation the account-plane second factor needs: SHA-1 · 6 digits ·
 * 30-second steps (auth-api.md § IdP 브라우저 화면 — 2단계 인증).
 *
 * <p>🔵 <b>A seam on purpose.</b> TASK-MONO-771 S2a moves admin-service's framework-free TOTP computation into
 * {@code libs/java-security} (project-agnostic, behaviour-identical). S2b runs in parallel with it, so this port
 * keeps the account plane independent of that move: today it is backed by a small local implementation
 * ({@code infrastructure.totp.Rfc6238TotpCodeCalculator}); <b>after S2a merges, the adapter is to be replaced by
 * the {@code libs/java-security} calculator</b> and the local one deleted — the callers of this port do not
 * change. Only the computation is shared; the secrets, their encryption keys and where they are verified stay
 * separate per plane (AC-0 § 3).
 *
 * <p>Deliberately narrow: no verification policy lives here. The ±1 window and the anti-replay counter are
 * decided by the application service against {@code AccountTotp}, so swapping the calculator cannot change
 * which codes are accepted.
 */
public interface TotpCodeCalculator {

    /** Seconds per TOTP step (RFC 6238 X). */
    long STEP_SECONDS = 30L;

    /** A new random shared secret (160 bits — RFC 4226 § 4 recommendation). */
    byte[] newSecret();

    /** The 6-digit code for {@code secret} at time-step {@code timeStep} (left-padded with zeros). */
    String codeAt(byte[] secret, long timeStep);

    /** RFC 4648 Base32 (no padding) — the form an authenticator app is given. */
    String toBase32(byte[] secret);

    /** The time-step of an instant: {@code floor(epochSecond / 30)}. */
    static long timeStepOf(java.time.Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), STEP_SECONDS);
    }
}
