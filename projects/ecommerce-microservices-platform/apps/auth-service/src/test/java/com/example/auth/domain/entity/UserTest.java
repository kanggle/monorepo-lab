package com.example.auth.domain.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User 도메인 테스트")
class UserTest {

    @Test
    @DisplayName("유효한 값으로 User를 생성할 수 있다")
    void create_success() {
        User user = User.create("test@example.com", "encodedPassword", "홍길동");

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail().value()).isEqualTo("test@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("encodedPassword");
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("이메일은 소문자로 정규화된다")
    void create_email_normalized() {
        User user = User.create("TEST@EXAMPLE.COM", "encodedPassword", "홍길동");

        assertThat(user.getEmail().value()).isEqualTo("test@example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    @DisplayName("이메일이 blank이면 예외가 발생한다")
    void create_blank_email_throws(String email) {
        assertThatThrownBy(() -> User.create(email, "encodedPassword", "홍길동"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "  "})
    @DisplayName("이름이 blank이면 예외가 발생한다")
    void create_blank_name_throws(String name) {
        assertThatThrownBy(() -> User.create("test@example.com", "encodedPassword", name))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Name");
    }

    @Test
    @DisplayName("이름이 50자를 초과하면 예외가 발생한다")
    void create_name_too_long_throws() {
        String longName = "a".repeat(51);

        assertThatThrownBy(() -> User.create("test@example.com", "encodedPassword", longName))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("50");
    }

    @Test
    @DisplayName("이름을 업데이트할 수 있다")
    void updateName_success() {
        User user = User.create("test@example.com", "encodedPassword", "홍길동");
        java.time.Instant before = user.getUpdatedAt();

        user.updateName("김철수");

        assertThat(user.getName()).isEqualTo("김철수");
        assertThat(user.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    @DisplayName("User.create() 결과의 createdAt이 Instant 타입이다")
    void create_createdAt_isInstant() {
        User user = User.create("instant@example.com", "encodedPassword", "테스트");

        assertThat(user.getCreatedAt()).isInstanceOf(java.time.Instant.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"notanemail", "missing-at-sign", "@nodomain.com", "noTLD@domain", "spaces in@email.com"})
    @DisplayName("이메일 포맷이 잘못된 경우 예외가 발생한다")
    void create_invalidEmailFormat_throws(String email) {
        assertThatThrownBy(() -> User.create(email, "encodedPassword", "홍길동"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "USER@EXAMPLE.COM", "user+tag@sub.domain.com"})
    @DisplayName("유효한 이메일 포맷으로 User를 생성할 수 있다")
    void create_validEmailFormats_success(String email) {
        assertThatCode(() -> User.create(email, "encodedPassword", "홍길동"))
            .doesNotThrowAnyException();
    }
}
