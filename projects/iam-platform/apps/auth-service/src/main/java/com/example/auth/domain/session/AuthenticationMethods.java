package com.example.auth.domain.session;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * TASK-MONO-771 (ADR-MONO-080 D4 · R3) — the RFC 8176 {@code amr} vocabulary this identity-platform emits, and
 * the one predicate a reader may apply to it.
 *
 * <p>The vocabulary is closed to three values ({@code platform/contracts/jwt-standard-claims.md} § {@code amr}):
 * <ul>
 *   <li>{@link #PWD} — the identity-platform's own password (form login);</li>
 *   <li>{@link #OTP} — a one-time code from the account's enrolled authenticator app;</li>
 *   <li>{@link #MFA} — more than one factor was used.</li>
 * </ul>
 * Per login: password {@code ["pwd"]} · password + app code {@code ["pwd","otp","mfa"]} · password + recovery
 * code {@code ["pwd","mfa"]} · social {@code []} · social + app code {@code ["otp","mfa"]}.
 *
 * <p>🔴 The only predicate is {@link #hasSecondFactor} ({@code "mfa" ∈ amr}). Nothing here or anywhere else
 * branches on the means values — so a later second factor (a passkey) changes no reader.
 *
 * <p>Pure domain helper — no framework type. Every list it returns is a fresh mutable {@link ArrayList}: the
 * value is stored in the SAS principal {@code details} map, whose Jackson allowlist rejects immutable lists.
 */
public final class AuthenticationMethods {

    /** The identity-platform's own password. */
    public static final String PWD = "pwd";

    /** A one-time code from the enrolled authenticator app. */
    public static final String OTP = "otp";

    /** More than one factor was used — the one value readers look at. */
    public static final String MFA = "mfa";

    private AuthenticationMethods() {
    }

    /** First factor = the identity-platform password. */
    public static ArrayList<String> password() {
        ArrayList<String> amr = new ArrayList<>();
        amr.add(PWD);
        return amr;
    }

    /**
     * First factor = an external provider. Deliberately empty: «authenticated, but by no factor this
     * identity-platform verified» (the contract's one {@code []}).
     */
    public static ArrayList<String> external() {
        return new ArrayList<>();
    }

    /**
     * The session's methods after the authenticator-app code was accepted: first factor + {@code otp} +
     * {@code mfa}. Idempotent (a value already present is not repeated).
     */
    public static ArrayList<String> withAuthenticatorCode(Collection<String> firstFactor) {
        ArrayList<String> amr = copy(firstFactor);
        addOnce(amr, OTP);
        addOnce(amr, MFA);
        return amr;
    }

    /**
     * The session's methods after a one-time recovery code was accepted: first factor + {@code mfa} — not
     * {@code otp}, a recovery code is not an authenticator-app code.
     */
    public static ArrayList<String> withRecoveryCode(Collection<String> firstFactor) {
        ArrayList<String> amr = copy(firstFactor);
        addOnce(amr, MFA);
        return amr;
    }

    /** {@code "mfa" ∈ amr}. {@code null} (no claim / no session value) reads as «no second factor». */
    public static boolean hasSecondFactor(Collection<String> amr) {
        return amr != null && amr.contains(MFA);
    }

    /**
     * Reads an {@code amr} value as it comes back out of a {@code details} map or a JWT claim: a list whose
     * String elements are kept in order. Anything else → {@code null} (absent — never guessed).
     */
    public static List<String> read(Object value) {
        if (!(value instanceof Collection<?> raw)) {
            return null;
        }
        ArrayList<String> amr = new ArrayList<>();
        for (Object element : raw) {
            if (element instanceof String s && !s.isBlank()) {
                amr.add(s);
            }
        }
        return amr;
    }

    private static ArrayList<String> copy(Collection<String> source) {
        ArrayList<String> amr = new ArrayList<>();
        if (source != null) {
            for (String s : source) {
                addOnce(amr, s);
            }
        }
        return amr;
    }

    private static void addOnce(List<String> amr, String value) {
        if (value != null && !amr.contains(value)) {
            amr.add(value);
        }
    }
}
