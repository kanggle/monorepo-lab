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
 * TASK-BE-267: Bean Validation unit tests for {@link ProvisionStatusChangeRequest}
 * confirming {@code operatorId} is bounded by
 * {@code account_status_history.actor_id VARCHAR(36)}.
 */
@DisplayName("ProvisionStatusChangeRequest validation — TASK-BE-267")
class ProvisionStatusChangeRequestValidationTest {

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
        ProvisionStatusChangeRequest request = new ProvisionStatusChangeRequest("ACTIVE", operatorId);

        Set<ConstraintViolation<ProvisionStatusChangeRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("operatorId 37자 → 위반")
    void operatorId_37chars_fails() {
        String operatorId = "o".repeat(37);
        ProvisionStatusChangeRequest request = new ProvisionStatusChangeRequest("ACTIVE", operatorId);

        Set<ConstraintViolation<ProvisionStatusChangeRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .anyMatch(path -> path.equals("operatorId"));
    }

    @Test
    @DisplayName("operatorId null → 통과 (선택값)")
    void operatorId_null_passes() {
        ProvisionStatusChangeRequest request = new ProvisionStatusChangeRequest("ACTIVE", null);

        Set<ConstraintViolation<ProvisionStatusChangeRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("status 누락 → 위반")
    void status_blank_fails() {
        ProvisionStatusChangeRequest request = new ProvisionStatusChangeRequest("", null);

        Set<ConstraintViolation<ProvisionStatusChangeRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .anyMatch(path -> path.equals("status"));
    }
}
