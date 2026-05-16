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
public interface GapOidcSubjectTokenValidator {

    /**
     * Validates the subject token end-to-end and returns the verified OIDC
     * subject (the {@code sub} claim = auth-service account_id UUID).
     *
     * @param subjectToken the raw GAP OIDC access token (RFC 8693
     *                      {@code subject_token})
     * @return the verified, non-blank OIDC subject
     * @throws com.example.admin.application.exception.SubjectTokenInvalidException
     *         if signature/iss/aud/exp/nbf fails, the token is not a GAP OIDC
     *         access token (carries a {@code token_type} claim), {@code sub}
     *         is absent, or the auth-service JWKS is unreachable (fail-closed)
     */
    String validateAndExtractSubject(String subjectToken);
}
