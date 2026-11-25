package com.example.account.presentation.advice;

import com.example.account.infrastructure.metrics.SignupMetricsProbe;
import com.example.account.presentation.SignupController;
import com.example.account.presentation.internal.ConsumerPoolSignupController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-668 — the {@code @Valid} arm of {@link GlobalExceptionHandler} counts
 * {@code account_signup_total{result="validation_error"}} for the public signup endpoint only: another
 * controller's validation failure (here the internal pool signup, which is not a public signup attempt)
 * is not counted, and a context without the metrics adapter still answers normally.
 */
class GlobalExceptionHandlerSignupMetricsTest {

    private static MethodArgumentNotValidException invalidBodyOf(Class<?> controller) {
        Method signup = Arrays.stream(controller.getDeclaredMethods())
                .filter(m -> m.getName().equals("signup"))
                .findFirst().orElseThrow();
        int bodyIndex = signup.getParameterCount() - 1;  // the @Valid @RequestBody is the last parameter
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "email", "Email is required"));
        return new MethodArgumentNotValidException(new MethodParameter(signup, bodyIndex), binding);
    }

    private static ObjectProvider<com.example.account.application.port.SignupMetricsPort> providerOf(
            SignupMetricsProbe probe) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (probe != null) {
            factory.addBean("signupMetrics", probe.port());
        }
        return factory.getBeanProvider(com.example.account.application.port.SignupMetricsPort.class);
    }

    @Test
    @DisplayName("public signup @Valid failure → 400 VALIDATION_ERROR and validation_error counted once")
    void publicSignupValidationFailureCounted() {
        SignupMetricsProbe probe = new SignupMetricsProbe();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(providerOf(probe));

        var response = handler.handleValidation(invalidBodyOf(SignupController.class));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        probe.assertOnly("validation_error");
    }

    @Test
    @DisplayName("another controller's @Valid failure is not a public signup attempt — not counted")
    void otherControllerValidationFailureNotCounted() {
        SignupMetricsProbe probe = new SignupMetricsProbe();
        GlobalExceptionHandler handler = new GlobalExceptionHandler(providerOf(probe));

        var response = handler.handleValidation(invalidBodyOf(ConsumerPoolSignupController.class));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        probe.assertNone();
    }

    @Test
    @DisplayName("no metrics adapter in the context → the answer is unchanged, nothing to count into")
    void noAdapterStillAnswers() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(providerOf(null));

        var response = handler.handleValidation(invalidBodyOf(SignupController.class));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }
}
