package com.example.auth.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogoutRequest 단위 테스트")
class LogoutRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 refreshToken은 검증을 통과한다")
    void validRefreshToken_noViolations() {
        LogoutRequest request = new LogoutRequest("valid-refresh-token");

        Set<ConstraintViolation<LogoutRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("refreshToken이 null이면 검증 실패")
    void refreshToken_null_invalid() {
        LogoutRequest request = new LogoutRequest(null);

        Set<ConstraintViolation<LogoutRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }

    @Test
    @DisplayName("refreshToken이 blank이면 검증 실패")
    void refreshToken_blank_invalid() {
        LogoutRequest request = new LogoutRequest("  ");

        Set<ConstraintViolation<LogoutRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }

    @Test
    @DisplayName("refreshToken이 512자를 초과하면 검증 실패")
    void refreshToken_tooLong_invalid() {
        LogoutRequest request = new LogoutRequest("a".repeat(513));

        Set<ConstraintViolation<LogoutRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }

    @Test
    @DisplayName("refreshToken이 512자이면 유효하다")
    void refreshToken_512chars_valid() {
        LogoutRequest request = new LogoutRequest("a".repeat(512));

        Set<ConstraintViolation<LogoutRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }
}
