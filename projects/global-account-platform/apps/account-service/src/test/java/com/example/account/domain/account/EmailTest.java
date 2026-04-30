package com.example.account.domain.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Email 값 객체 검증 테스트")
class EmailTest {

    @Test
    @DisplayName("유효한 이메일이 정상 생성된다")
    void create_validEmail_succeeds() {
        Email email = new Email("user@example.com");
        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("대문자 이메일이 소문자로 정규화된다")
    void create_uppercaseEmail_normalizedToLowercase() {
        Email email = new Email("User@Example.COM");
        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("앞뒤 공백이 제거된다")
    void create_emailWithSpaces_trimmed() {
        Email email = new Email("  user@example.com  ");
        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("null 이메일이 예외를 발생시킨다")
    void create_nullEmail_throwsException() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("빈 문자열 이메일이 예외를 발생시킨다")
    void create_emptyEmail_throwsException() {
        assertThatThrownBy(() -> new Email(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("공백만 있는 이메일이 예외를 발생시킨다")
    void create_blankEmail_throwsException() {
        assertThatThrownBy(() -> new Email("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"notanemail", "@example.com", "user@", "user@.com", "user@example."})
    @DisplayName("잘못된 형식의 이메일이 예외를 발생시킨다")
    void create_invalidFormat_throwsException(String invalidEmail) {
        assertThatThrownBy(() -> new Email(invalidEmail))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "user@example.com",
            "user.name@example.com",
            "user+tag@example.com",
            "user@sub.example.com"
    })
    @DisplayName("다양한 유효 이메일 형식이 정상 처리된다")
    void create_variousValidFormats_succeeds(String validEmail) {
        Email email = new Email(validEmail);
        assertThat(email.value()).isEqualTo(validEmail.toLowerCase());
    }
}
