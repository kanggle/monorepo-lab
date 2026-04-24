package com.example.auth.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LoginRequest 단위 테스트")
class LoginRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 email과 password는 검증을 통과한다")
    void validRequest_noViolations() {
        LoginRequest request = new LoginRequest("test@example.com", "password1!");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("email이 null이면 검증 실패")
    void email_null_invalid() {
        LoginRequest request = new LoginRequest(null, "password1!");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("email이 blank이면 검증 실패")
    void email_blank_invalid() {
        LoginRequest request = new LoginRequest("  ", "password1!");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("email 형식이 올바르지 않으면 검증 실패")
    void email_invalidFormat_invalid() {
        LoginRequest request = new LoginRequest("not-an-email", "password1!");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("email이 255자를 초과하면 검증 실패")
    void email_tooLong_invalid() {
        String email = "a".repeat(248) + "@a.com";
        LoginRequest request = new LoginRequest(email, "password1!");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @Test
    @DisplayName("password가 null이면 검증 실패")
    void password_null_invalid() {
        LoginRequest request = new LoginRequest("test@example.com", null);

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("password가 blank이면 검증 실패")
    void password_blank_invalid() {
        LoginRequest request = new LoginRequest("test@example.com", "  ");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("password가 7자이면 검증 실패")
    void password_tooShort_invalid() {
        LoginRequest request = new LoginRequest("test@example.com", "pass12!");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("password가 8자이면 유효하다")
    void password_8chars_valid() {
        LoginRequest request = new LoginRequest("test@example.com", "passwo1!");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("password 128자는 유효하다")
    void password_128chars_valid() {
        String password = "a".repeat(128);
        LoginRequest request = new LoginRequest("test@example.com", password);

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("password 129자 초과 시 검증 실패")
    void password_129chars_invalid() {
        String password = "a".repeat(129);
        LoginRequest request = new LoginRequest("test@example.com", password);

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }
}
