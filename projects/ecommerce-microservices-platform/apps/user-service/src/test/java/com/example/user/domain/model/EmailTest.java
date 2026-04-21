package com.example.user.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Email Value Object 테스트")
class EmailTest {

    @Test
    @DisplayName("유효한 이메일로 Email을 생성할 수 있다")
    void of_validEmail_success() {
        Email email = Email.of("test@example.com");

        assertThat(email.value()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("이메일은 소문자로 정규화된다")
    void of_uppercaseEmail_normalizedToLowercase() {
        Email email = Email.of("TEST@EXAMPLE.COM");

        assertThat(email.value()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("이메일 앞뒤 공백이 제거된다")
    void of_emailWithWhitespace_trimmed() {
        Email email = Email.of("  test@example.com  ");

        assertThat(email.value()).isEqualTo("test@example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    @DisplayName("null 또는 빈 문자열이면 예외가 발생한다")
    void of_blankEmail_throws(String email) {
        assertThatThrownBy(() -> Email.of(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-email", "user@", "user@domain", "@domain.com"})
    @DisplayName("이메일 포맷이 잘못된 경우 예외가 발생한다")
    void of_invalidFormat_throws(String email) {
        assertThatThrownBy(() -> Email.of(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email format is invalid");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "user.name+tag@example.co.kr", "Test@Example.COM"})
    @DisplayName("유효한 이메일 포맷으로 Email을 생성할 수 있다")
    void of_validFormats_success(String email) {
        assertThatCode(() -> Email.of(email))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("대소문자가 다른 이메일은 정규화 후 동일하다")
    void equals_caseInsensitive_equal() {
        Email email1 = Email.of("Test@Example.COM");
        Email email2 = Email.of("test@example.com");

        assertThat(email1).isEqualTo(email2);
    }

    @Test
    @DisplayName("최대 길이(254자)를 초과하면 예외가 발생한다")
    void of_exceedsMaxLength_throws() {
        String localPart = "a".repeat(243);
        String overLengthEmail = localPart + "@example.com"; // 255 chars

        assertThatThrownBy(() -> Email.of(overLengthEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email must not exceed 254 characters");
    }

    @Test
    @DisplayName("최대 길이(254자) 이메일은 허용된다")
    void of_exactMaxLength_success() {
        String localPart = "a".repeat(242);
        String maxLengthEmail = localPart + "@example.com"; // 254 chars

        assertThatCode(() -> Email.of(maxLengthEmail))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("같은 이메일 값을 가진 Email 객체는 동일하다")
    void equals_sameValue_equal() {
        Email email1 = Email.of("test@example.com");
        Email email2 = Email.of("test@example.com");

        assertThat(email1).isEqualTo(email2);
        assertThat(email1.hashCode()).isEqualTo(email2.hashCode());
    }

    @Test
    @DisplayName("다른 이메일 값을 가진 Email 객체는 다르다")
    void equals_differentValue_notEqual() {
        Email email1 = Email.of("a@example.com");
        Email email2 = Email.of("b@example.com");

        assertThat(email1).isNotEqualTo(email2);
    }

    @Test
    @DisplayName("toString은 이메일 값을 반환한다")
    void toString_returnsValue() {
        Email email = Email.of("test@example.com");

        assertThat(email.toString()).isEqualTo("test@example.com");
    }
}
