package com.example.auth.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SignupRequest 단위 테스트")
class SignupRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("128자 비밀번호는 유효하다")
    void password_128chars_valid() {
        String password = "a1!" + "a".repeat(125);
        SignupRequest request = new SignupRequest("test@example.com", password, "홍길동");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("129자 비밀번호는 유효하지 않다")
    void password_129chars_invalid() {
        String password = "a1!" + "a".repeat(126);
        SignupRequest request = new SignupRequest("test@example.com", password, "홍길동");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations)
            .isNotEmpty()
            .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("한글 포함 128자 비밀번호는 유효하다")
    void password_128chars_multibyte_valid() {
        String password = "a1!" + "가".repeat(125);
        SignupRequest request = new SignupRequest("test@example.com", password, "홍길동");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("특수문자 없는 비밀번호는 유효하지 않다")
    void password_noSpecialChar_invalid() {
        SignupRequest request = new SignupRequest("test@example.com", "password1", "홍길동");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations)
            .isNotEmpty()
            .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("특수문자 포함 비밀번호는 유효하다")
    void password_withSpecialChar_valid() {
        SignupRequest request = new SignupRequest("test@example.com", "password1!", "홍길동");

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
