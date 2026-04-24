package com.example.product.presentation.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UpdateProductRequest 단위 테스트")
class UpdateProductRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    @DisplayName("유효한 요청은 검증을 통과한다")
    void validRequest_noViolations() {
        UpdateProductRequest request = new UpdateProductRequest("상품명", "설명", 10000L, null);

        Set<ConstraintViolation<UpdateProductRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("price가 null이면 검증을 통과한다 (부분 수정 허용)")
    void price_null_valid() {
        UpdateProductRequest request = new UpdateProductRequest("상품명", null, null, null);

        Set<ConstraintViolation<UpdateProductRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("price가 0이면 검증을 통과한다")
    void price_zero_valid() {
        UpdateProductRequest request = new UpdateProductRequest(null, null, 0L, null);

        Set<ConstraintViolation<UpdateProductRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("price가 음수이면 검증 실패")
    void price_negative_invalid() {
        UpdateProductRequest request = new UpdateProductRequest(null, null, -1L, null);

        Set<ConstraintViolation<UpdateProductRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }

    @Test
    @DisplayName("price가 -1000이면 검증 실패")
    void price_negativeThousand_invalid() {
        UpdateProductRequest request = new UpdateProductRequest("상품명", "설명", -1000L, null);

        Set<ConstraintViolation<UpdateProductRequest>> violations = validator.validate(request);

        assertThat(violations)
                .hasSize(1)
                .anyMatch(v -> v.getPropertyPath().toString().equals("price"));
    }
}
