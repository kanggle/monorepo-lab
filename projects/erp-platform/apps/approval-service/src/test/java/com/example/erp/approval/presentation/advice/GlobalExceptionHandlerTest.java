package com.example.erp.approval.presentation.advice;

import com.example.erp.approval.domain.error.ApprovalErrors;
import com.example.erp.approval.presentation.dto.ApiErrorBody;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}. Invokes each handler directly —
 * this class is a pure exception→envelope mapper, so Spring MVC buys nothing.
 *
 * <p>Asserts the {@code (code, HTTP status)} contract of approval-api.md § Error
 * code → HTTP. The service shipped without this test, which is how the
 * {@code IllegalStateException} handler drifted (TASK-MONO-348).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /**
     * A known state-machine transition has its own code
     * ({@code APPROVAL_STATUS_TRANSITION_INVALID}); this catch-all is for the
     * <i>unclassified</i> invariant break, and the registered code for that is
     * {@code ILLEGAL_STATE} (platform/error-handling.md § General).
     */
    @Test
    @DisplayName("IllegalStateException → 422 ILLEGAL_STATE (never VALIDATION_ERROR)")
    void illegalStateUsesTheRegisteredCode() {
        ResponseEntity<ApiErrorBody> r = handler.handleIllegalState(
                new IllegalStateException("route has no steps"));

        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "ILLEGAL_STATE");
        assertThat(r.getBody().code())
                .as("VALIDATION_ERROR is registered at 400; emitting it at 422 splits the code")
                .isNotEqualTo("VALIDATION_ERROR");
    }

    /**
     * The invariant {@code STATUS_BY_CODE} exists to hold, asserted directly: a client that
     * branches on {@code code} must be able to rely on the status that comes with it. Revert
     * the {@code handleIllegalState} fix and {@code VALIDATION_ERROR} appears at both 400 and
     * 422 — this test fails.
     */
    @Test
    @DisplayName("no code leaves this service at two different HTTP statuses")
    void oneCodeOneStatus() {
        List<ResponseEntity<ApiErrorBody>> everyHandler = List.of(
                handler.handleIllegalArgument(new IllegalArgumentException("bad argument")),
                handler.handleIllegalState(new IllegalStateException("invariant")),
                handler.handleValidation(beanValidationFailure()),
                handler.handleMalformed(malformedBody()),
                handler.handleTypeMismatch(typeMismatch("requestId")),
                handler.handleMissingHeader(missingHeader("Idempotency-Key")),
                handler.handleMissingHeader(missingHeader("X-Custom")),
                handler.handleOptimisticLock(new OptimisticLockException("version stale")),
                handler.handleDomain(new ApprovalErrors.ApprovalRequestNotFoundException("nope")),
                handler.handleGeneral(new RuntimeException("boom")));

        Map<String, Set<HttpStatusCode>> statusesByCode = new LinkedHashMap<>();
        for (ResponseEntity<ApiErrorBody> r : everyHandler) {
            assertThat(r.getBody()).isNotNull();
            statusesByCode
                    .computeIfAbsent(r.getBody().code(), k -> new LinkedHashSet<>())
                    .add(r.getStatusCode());
        }

        assertThat(statusesByCode).isNotEmpty();
        assertThat(statusesByCode).allSatisfy((code, statuses) -> assertThat(statuses)
                .as("code %s is emitted at more than one status: %s", code, statuses)
                .hasSize(1));
    }

    @Test
    @DisplayName("every VALIDATION_ERROR path is 400 (malformed body, type mismatch, bad argument)")
    void validationErrorIsAlways400() {
        assertStatus(handler.handleMalformed(malformedBody()),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertStatus(handler.handleTypeMismatch(typeMismatch("requestId")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertStatus(handler.handleIllegalArgument(new IllegalArgumentException("bad argument")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("domain codes resolve to their registered status")
    void domainCodes() {
        assertStatus(handler.handleDomain(new ApprovalErrors.ApprovalRequestNotFoundException("nope")),
                HttpStatus.NOT_FOUND, "APPROVAL_REQUEST_NOT_FOUND");
        assertStatus(handler.handleDomain(
                        new ApprovalErrors.ApprovalStatusTransitionInvalidException("bad transition")),
                HttpStatus.CONFLICT, "APPROVAL_STATUS_TRANSITION_INVALID");
        assertStatus(handler.handleDomain(
                        new ApprovalErrors.ApprovalAlreadyFinalizedException("terminal")),
                HttpStatus.CONFLICT, "APPROVAL_ALREADY_FINALIZED");
    }

    @Test
    @DisplayName("optimistic lock → 409 CONCURRENT_MODIFICATION")
    void optimisticLock() {
        assertStatus(handler.handleOptimisticLock(new OptimisticLockException("version stale")),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
    }

    @Test
    @DisplayName("missing Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED (case-insensitive)")
    void missingIdempotencyHeader() {
        assertStatus(handler.handleMissingHeader(missingHeader("Idempotency-Key")),
                HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        assertStatus(handler.handleMissingHeader(missingHeader("idempotency-key")),
                HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
    }

    /**
     * {@code INTERNAL_ERROR} is absent from {@code STATUS_BY_CODE}, so routing this terminal
     * catch-all through the table's {@code getOrDefault} fallback would silently downgrade a
     * 500 to a 422. It stays hard-coded — this test is the tripwire.
     */
    @Test
    @DisplayName("generic Exception → 500 INTERNAL_ERROR without leaking detail")
    void generic() {
        ResponseEntity<ApiErrorBody> r = handler.handleGeneral(new RuntimeException("secret crash"));
        assertStatus(r, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(r.getBody().message()).doesNotContain("secret crash");
    }

    // ---------------- helpers ----------------

    private static void assertStatus(ResponseEntity<ApiErrorBody> r, HttpStatus expected, String code) {
        assertThat(r.getStatusCode()).isEqualTo(expected);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo(code);
        assertThat(r.getBody().timestamp()).isNotNull();
    }

    private static HttpMessageNotReadableException malformedBody() {
        return new HttpMessageNotReadableException("malformed", (HttpInputMessage) null);
    }

    private static MethodArgumentTypeMismatchException typeMismatch(String name) {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn(name);
        return ex;
    }

    private static MissingRequestHeaderException missingHeader(String name) {
        MissingRequestHeaderException ex = mock(MissingRequestHeaderException.class);
        when(ex.getHeaderName()).thenReturn(name);
        return ex;
    }

    private static MethodArgumentNotValidException beanValidationFailure() {
        BindingResult bindingResult = mock(BindingResult.class);
        when(bindingResult.getFieldErrors())
                .thenReturn(List.of(new FieldError("req", "reason", "must not be blank")));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        return ex;
    }
}
