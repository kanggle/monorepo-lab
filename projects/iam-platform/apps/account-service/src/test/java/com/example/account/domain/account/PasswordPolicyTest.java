package com.example.account.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TASK-BE-473 — account-service signup-boundary password policy. Kept in lock-step with
 * auth-service's {@code PasswordPolicyTest} (same rules); see {@link PasswordPolicy} javadoc.
 */
class PasswordPolicyTest {

    private static final String EMAIL = "alice@example.com";

    @Test
    @DisplayName("Password shorter than min length throws")
    void validate_tooShort_throws() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Ab1!xyz", EMAIL)) // 7 chars
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("at least");
    }

    @Test
    @DisplayName("Null password throws (length rule)")
    void validate_null_throws() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null, EMAIL))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("at least");
    }

    @Test
    @DisplayName("Password longer than max length throws")
    void validate_tooLong_throws() {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 129) {
            sb.append("Ab1!");
        }
        assertThatThrownBy(() -> PasswordPolicy.validate(sb.substring(0, 129), EMAIL))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("at most");
    }

    @Test
    @DisplayName("Only-lowercase password fails the 3-of-4 complexity rule")
    void validate_onlyLowercase_throws() {
        assertThatThrownBy(() -> PasswordPolicy.validate("abcdefghij", EMAIL)) // 1 of 4 categories
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("3");
    }

    @Test
    @DisplayName("Password containing the email (case-insensitive) is rejected")
    void validate_containsEmail_throws() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Prefix-ALICE@EXAMPLE.COM-1!", EMAIL))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("Valid password (lower+digit+special = 3 of 4) passes — e.g. the user's test1234!")
    void validate_threeOfFour_passes() {
        assertThatCode(() -> PasswordPolicy.validate("test1234!", EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Null email skips the email-containment check (defensive)")
    void validate_nullEmail_skipsEmailCheck() {
        assertThatCode(() -> PasswordPolicy.validate("Str0ng!pass", null))
                .doesNotThrowAnyException();
    }
}
