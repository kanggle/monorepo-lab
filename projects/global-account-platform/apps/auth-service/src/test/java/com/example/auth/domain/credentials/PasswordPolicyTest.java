package com.example.auth.domain.credentials;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static final String EMAIL = "alice@example.com";

    @Test
    @DisplayName("Password shorter than min length throws")
    void validate_tooShort_throws() {
        // 7 chars
        String pwd = "Ab1!xyz";

        assertThatThrownBy(() -> PasswordPolicy.validate(pwd, EMAIL))
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
        // 129 chars: 'A' + 'b' + '1' + '!' repeated, then padded
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 129) {
            sb.append("Ab1!");
        }
        String pwd = sb.substring(0, 129);

        assertThatThrownBy(() -> PasswordPolicy.validate(pwd, EMAIL))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("at most");
    }

    @Test
    @DisplayName("Lowercase-only password fails complexity rule")
    void validate_onlyLowercase_throws() {
        String pwd = "abcdefghij"; // 10 lowercase, only 1 of 4 categories

        assertThatThrownBy(() -> PasswordPolicy.validate(pwd, EMAIL))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("3");
    }

    @Test
    @DisplayName("Password equal to email is rejected")
    void validate_matchesEmail_throws() {
        // Use an email that itself satisfies length+complexity to force the
        // email-containment rule to be the failing check.
        String email = "Alice1!@example.com"; // 19 chars, has upper/lower/digit/special
        String pwd = email; // identical

        assertThatThrownBy(() -> PasswordPolicy.validate(pwd, email))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("Password containing email substring (case-insensitive) is rejected")
    void validate_containsEmail_throws() {
        String email = "alice@example.com";
        String pwd = "Prefix-ALICE@EXAMPLE.COM-1!"; // contains email upper-cased

        assertThatThrownBy(() -> PasswordPolicy.validate(pwd, email))
                .isInstanceOf(PasswordPolicyViolationException.class)
                .hasMessageContaining("email");
    }

    @Test
    @DisplayName("Valid password passes all rules")
    void validate_valid_passes() {
        String pwd = "Str0ngP@ss"; // upper+lower+digit+special, 10 chars, no email

        assertThatCode(() -> PasswordPolicy.validate(pwd, EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Null email skips email-containment check (defensive)")
    void validate_nullEmail_skipsEmailCheck() {
        String pwd = "Str0ngP@ss";

        assertThatCode(() -> PasswordPolicy.validate(pwd, null))
                .doesNotThrowAnyException();
    }
}
