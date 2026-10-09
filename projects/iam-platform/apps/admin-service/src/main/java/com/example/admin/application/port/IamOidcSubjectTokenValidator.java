package com.example.admin.application.port;

/**
 * TASK-BE-298 / ADR-MONO-014 — application port that validates a GAP OIDC
 * {@code platform-console-web} subject token and extracts the OIDC subject.
 *
 * <p>The implementation (infrastructure adapter) verifies the token against
 * the auth-service JWKS and enforces {@code iss}/{@code aud}/{@code exp}/
 * {@code nbf}/RS256 + the "no {@code token_type} claim" guard, per
 * {@code specs/services/admin-service/security.md} §GAP OIDC Subject-Token
 * Validation. Any validation ambiguity is signalled as
 * {@code SubjectTokenInvalidException} so the application service can fail
 * closed with {@code 401} and never mint a token.
 *
 * <p>Keeping this as an application port keeps the JWKS HTTP client / JJWT
 * parser confined to {@code infrastructure} (architecture.md Allowed
 * Dependencies — the application layer depends only on this abstraction).
 */
public interface IamOidcSubjectTokenValidator {

    /**
     * TASK-MONO-771 (security.md § IAM OIDC Subject-Token Validation row 7) — validates the subject
     * token end-to-end and returns the verified {@code sub} together with the token's {@code amr}.
     *
     * <p>{@code amr} is <b>extracted</b>, not validated: absent, not an array, or non-string elements
     * all read as an empty set (= «no second factor», fail-closed) — never a {@code 401} by itself.
     *
     * @throws com.example.admin.application.exception.SubjectTokenInvalidException
     *         on exactly the failures {@link #validateAndExtractSubject} lists (fail-closed)
     */
    ValidatedSubject validate(String subjectToken);

    /**
     * Validates the subject token end-to-end and returns the verified OIDC
     * subject (the {@code sub} claim = auth-service account_id UUID).
     *
     * <p>TASK-MONO-771: kept as the {@code sub}-only view of {@link #validate} — the self-service
     * onboarding entry ({@code OnboardingController}) reads {@code sub} only and its decision does
     * not change with {@code amr} (security.md row 7).
     *
     * @param subjectToken the raw GAP OIDC access token (RFC 8693
     *                      {@code subject_token})
     * @return the verified, non-blank OIDC subject
     * @throws com.example.admin.application.exception.SubjectTokenInvalidException
     *         if signature/iss/aud/exp/nbf fails, the token is not a GAP OIDC
     *         access token (carries a {@code token_type} claim), {@code sub}
     *         is absent, or the auth-service JWKS is unreachable (fail-closed)
     */
    default String validateAndExtractSubject(String subjectToken) {
        return validate(subjectToken).subject();
    }

    /**
     * The verified subject + its RFC 8176 authentication methods.
     *
     * @param subject the verified, non-blank {@code sub} (account_id)
     * @param amr     the token's {@code amr} values; never {@code null} (absent ⇒ empty)
     */
    record ValidatedSubject(String subject, java.util.Set<String> amr) {

        /** The one value a reader looks at (jwt-standard-claims.md § {@code amr}). */
        public static final String MFA = "mfa";

        public ValidatedSubject {
            amr = amr == null ? java.util.Set.of() : java.util.Set.copyOf(amr);
        }

        /** {@code "mfa" ∈ amr} — the only predicate (never the means values such as {@code otp}). */
        public boolean hasSecondFactor() {
            return amr.contains(MFA);
        }
    }
}
