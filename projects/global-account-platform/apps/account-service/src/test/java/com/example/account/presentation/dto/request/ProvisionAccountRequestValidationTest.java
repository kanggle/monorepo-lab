package com.example.account.presentation.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-267: Bean Validation unit tests for {@link ProvisionAccountRequest}
 * confirming role_name max length is aligned with the DB column (VARCHAR(64))
 * and {@code operatorId} is bounded by {@code granted_by VARCHAR(36)}.
 */
@DisplayName("ProvisionAccountRequest validation — TASK-BE-267")
class ProvisionAccountRequestValidationTest {

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

    private static ProvisionAccountRequest baseRequest(List<String> roles, String operatorId) {
        return new ProvisionAccountRequest(
                "user@example.com",
                "password123",
                "Test User",
                "ko-KR",
                "Asia/Seoul",
                roles,
                operatorId
        );
    }

    @Test
    @DisplayName("role_name 64자 → 통과")
    void roleName_64chars_passes() {
        String roleName = "A".repeat(64);
        ProvisionAccountRequest request = baseRequest(List.of(roleName), null);

        Set<ConstraintViolation<ProvisionAccountRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("role_name 65자 → 위반")
    void roleName_65chars_fails() {
        String roleName = "A".repeat(65);
        ProvisionAccountRequest request = baseRequest(List.of(roleName), null);

        Set<ConstraintViolation<ProvisionAccountRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .anyMatch(msg -> msg.contains("64 characters"));
    }

    @Test
    @DisplayName("operatorId 36자 → 통과")
    void operatorId_36chars_passes() {
        String operatorId = "o".repeat(36);
        ProvisionAccountRequest request = baseRequest(List.of("ADMIN"), operatorId);

        Set<ConstraintViolation<ProvisionAccountRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("operatorId 37자 → 위반")
    void operatorId_37chars_fails() {
        String operatorId = "o".repeat(37);
        ProvisionAccountRequest request = baseRequest(List.of("ADMIN"), operatorId);

        Set<ConstraintViolation<ProvisionAccountRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .anyMatch(path -> path.equals("operatorId"));
    }

    @Test
    @DisplayName("operatorId null → 통과 (선택값)")
    void operatorId_null_passes() {
        ProvisionAccountRequest request = baseRequest(List.of("ADMIN"), null);

        Set<ConstraintViolation<ProvisionAccountRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
