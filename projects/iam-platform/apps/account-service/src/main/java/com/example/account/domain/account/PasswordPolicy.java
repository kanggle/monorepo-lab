package com.example.account.domain.account;

/**
 * TASK-BE-473: signup-boundary password-complexity validation.
 *
 * <p>Mirrors auth-service's {@code com.example.auth.domain.credentials.PasswordPolicy}
 * (the credential-write authority used by password change/reset). account-service validates
 * complexity <b>here</b> — at the signup boundary, before the account row is created
 * ({@code specs/features/signup.md} §User Flow step 5, which precedes step 6) — so a weak
 * password is rejected with a 422 {@code VALIDATION_ERROR}
 * ({@code specs/contracts/http/account-api.md}) and never reaches the account/credential
 * write path (no wasted account row, and no auth-service round trip that would otherwise
 * count against the outbound circuit breaker).</p>
 *
 * <p>The two implementations intentionally share the same rules (8..128 chars, at least 3 of
 * {uppercase, lowercase, digit, special}, no email containment). A future refactor may promote
 * a single implementation to a shared library ({@code libs/java-security}, next to
 * {@code PasswordHasher}); until then both are kept byte-identical.</p>
 *
 * <p>Per {@code rules/traits/regulated.md} R4 the plaintext password is never logged, stored,
 * or echoed back in the violation message.</p>
 */
public final class PasswordPolicy {

    static final int MIN_LENGTH = 8;
    static final int MAX_LENGTH = 128;
    static final int MIN_COMPLEXITY_SCORE = 3;

    private PasswordPolicy() {
        // utility class
    }

    /**
     * Validate the given plaintext password.
     *
     * @param plainPassword the candidate password (plaintext, never logged)
     * @param email         the account email to forbid embedding; may be {@code null}
     *                      (defensive — the email-containment check is then skipped)
     * @throws PasswordPolicyViolationException if any rule is violated
     */
    public static void validate(String plainPassword, String email) {
        if (plainPassword == null || plainPassword.length() < MIN_LENGTH) {
            throw new PasswordPolicyViolationException(
                    "Password must be at least " + MIN_LENGTH + " characters");
        }
        if (plainPassword.length() > MAX_LENGTH) {
            throw new PasswordPolicyViolationException(
                    "Password must be at most " + MAX_LENGTH + " characters");
        }

        int score = 0;
        if (plainPassword.chars().anyMatch(Character::isUpperCase)) {
            score++;
        }
        if (plainPassword.chars().anyMatch(Character::isLowerCase)) {
            score++;
        }
        if (plainPassword.chars().anyMatch(Character::isDigit)) {
            score++;
        }
        if (plainPassword.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            score++;
        }

        if (score < MIN_COMPLEXITY_SCORE) {
            throw new PasswordPolicyViolationException(
                    "Password must contain at least " + MIN_COMPLEXITY_SCORE
                            + " of: uppercase, lowercase, digit, special character");
        }

        if (email != null && !email.isBlank()
                && plainPassword.toLowerCase().contains(email.toLowerCase())) {
            throw new PasswordPolicyViolationException(
                    "Password must not contain the email address");
        }
    }
}
