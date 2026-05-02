package com.example.account.presentation.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-265: Bean Validation unit tests for {@link SingleRoleMutationRequest}.
 * The {@code operatorId} max length must equal the {@code granted_by VARCHAR(36)}
 * column, otherwise the request is forwarded to the DB and 500s on truncation.
 */
@DisplayName("SingleRoleMutationRequest validation — TASK-BE-265")
class SingleRoleMutationRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("operatorId 36자 → 통과")
    void operatorId_36chars_passes() {
        String operatorId = "o".repeat(36);
        SingleRoleMutationRequest request = new SingleRoleMutationRequest("ADMIN", operatorId);

        Set<ConstraintViolation<SingleRoleMutationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("operatorId 37자 → 위반")
    void operatorId_37chars_fails() {
        String operatorId = "o".repeat(37);
        SingleRoleMutationRequest request = new SingleRoleMutationRequest("ADMIN", operatorId);

        Set<ConstraintViolation<SingleRoleMutationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .anyMatch(path -> path.equals("operatorId"));
    }

    @Test
    @DisplayName("roleName 64자 → 통과")
    void roleName_64chars_passes() {
        String roleName = "A".repeat(64);
        SingleRoleMutationRequest request = new SingleRoleMutationRequest(roleName, null);

        Set<ConstraintViolation<SingleRoleMutationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("roleName 65자 → 위반")
    void roleName_65chars_fails() {
        String roleName = "A".repeat(65);
        SingleRoleMutationRequest request = new SingleRoleMutationRequest(roleName, null);

        Set<ConstraintViolation<SingleRoleMutationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .anyMatch(msg -> msg.contains("64 characters"));
    }

    @Test
    @DisplayName("roleName 빈 문자열 → 위반 (NotBlank)")
    void roleName_blank_fails() {
        SingleRoleMutationRequest request = new SingleRoleMutationRequest("", null);

        Set<ConstraintViolation<SingleRoleMutationRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
    }
}
