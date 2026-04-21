package com.example.auth.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RefreshRequest 단위 테스트")
class RefreshRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 refreshToken은 검증을 통과한다")
    void validRequest_noViolations() {
        RefreshRequest request = new RefreshRequest("valid-refresh-token");

        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("refreshToken이 null이면 검증 실패")
    void refreshToken_null_invalid() {
        RefreshRequest request = new RefreshRequest(null);

        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }

    @Test
    @DisplayName("refreshToken이 blank이면 검증 실패")
    void refreshToken_blank_invalid() {
        RefreshRequest request = new RefreshRequest("  ");

        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }

    @Test
    @DisplayName("refreshToken이 9자이면 검증 실패 (최소 10자)")
    void refreshToken_tooShort_invalid() {
        RefreshRequest request = new RefreshRequest("a".repeat(9));

        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }

    @Test
    @DisplayName("refreshToken이 10자이면 유효하다")
    void refreshToken_10chars_valid() {
        RefreshRequest request = new RefreshRequest("a".repeat(10));

        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("refreshToken이 512자를 초과하면 검증 실패")
    void refreshToken_tooLong_invalid() {
        RefreshRequest request = new RefreshRequest("a".repeat(513));

        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }

    @Test
    @DisplayName("refreshToken이 512자이면 유효하다")
    void refreshToken_512chars_valid() {
        RefreshRequest request = new RefreshRequest("a".repeat(512));

        Set<ConstraintViolation<RefreshRequest>> violations = validator.validate(request);

        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("refreshToken"));
    }
}
