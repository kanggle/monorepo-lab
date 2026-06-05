package com.example.auth.domain.credentials;

/**
 * Domain service that validates plaintext passwords against the project's
 * password policy (specs/features/password-management.md).
 *
 * <p>Pure POJO — no Spring dependencies. Throws
 * {@link PasswordPolicyViolationException} when the password violates a rule.</p>
 *
 * <p>Rules:</p>
 * <ul>
 *   <li>Length: 8 to 128 characters inclusive.</li>
 *   <li>Complexity: at least 3 of {uppercase, lowercase, digit, special}.</li>
 *   <li>Email containment: password (case-insensitive) must not contain the
 *       associated email address.</li>
 * </ul>
 *
 * <p>Per R4 (rules/traits/regulated.md) the plaintext password is never logged,
 * stored, or echoed back in the violation message.</p>
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
     *                      (defensive — email-equality check is then skipped)
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
