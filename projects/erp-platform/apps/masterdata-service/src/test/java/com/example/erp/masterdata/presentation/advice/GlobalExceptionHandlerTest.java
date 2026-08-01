package com.example.erp.masterdata.presentation.advice;

import com.example.erp.masterdata.domain.error.DomainErrors;
import com.example.erp.masterdata.presentation.dto.ApiErrorBody;
import com.example.web.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
 * this class is a pure exception→envelope mapper, so Spring MVC buys nothing here.
 * (The <em>reachability</em> of the inherited arms — that {@code extends
 * CommonGlobalExceptionHandler} actually registers them with Spring's resolver — is
 * proven separately by {@link GlobalExceptionHandlerNotFoundTest}, which drives
 * MockMvc.)
 *
 * <p>Asserts the {@code (code, HTTP status)} contract of masterdata-api.md § Error
 * code → HTTP. The service shipped without this test, which is how the
 * {@code IllegalStateException} handler drifted (TASK-MONO-348).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("IllegalStateException → 422 ILLEGAL_STATE (never VALIDATION_ERROR)")
    void illegalStateUsesTheRegisteredCode() {
        ResponseEntity<ErrorResponse> r = handler.handleIllegalState(
                new IllegalStateException("revision chain broken"));

        assertStatus(r, HttpStatus.UNPROCESSABLE_ENTITY, "ILLEGAL_STATE");
        assertThat(r.getBody().code())
                .as("VALIDATION_ERROR is registered at 400; emitting it at 422 splits the code")
                .isNotEqualTo("VALIDATION_ERROR");
    }

    /**
     * The invariant {@code STATUS_BY_CODE} exists to hold, asserted directly: a client that
     * branches on {@code code} must be able to rely on the status that comes with it. Revert
     * the {@code handleIllegalState} fix and {@code VALIDATION_ERROR} appears at both 400 and
     * 422 — this test fails. The inherited arms are included because after ADR-MONO-058 § D2
     * they are part of this service's emitted surface too.
     */
    @Test
    @DisplayName("no code leaves this service at two different HTTP statuses")
    void oneCodeOneStatus() {
        List<CodeStatus> everyHandler = List.of(
                shared(handler.handleIllegalArgument(new IllegalArgumentException("bad argument"))),
                shared(handler.handleIllegalState(new IllegalStateException("invariant"))),
                shared(handler.handleValidation(beanValidationFailure())),
                shared(handler.handleMalformedRequest(malformedBody())),
                shared(handler.handleTypeMismatch(typeMismatch("departmentId"))),
                shared(handler.handleMissingHeader(missingHeader("Idempotency-Key"))),
                shared(handler.handleMissingHeader(missingHeader("X-Custom"))),
                shared(handler.handleJpaOptimisticLock(new OptimisticLockException("version stale"))),
                shared(handler.handleOptimisticLock(
                        new ObjectOptimisticLockingFailureException("Department", "d-1"))),
                envelope(handler.handleDomain(new DomainErrors.MasterdataNotFoundException("nope"))),
                shared(handler.handleGeneral(new RuntimeException("boom"))));

        Map<String, Set<HttpStatusCode>> statusesByCode = new LinkedHashMap<>();
        for (CodeStatus r : everyHandler) {
            statusesByCode
                    .computeIfAbsent(r.code(), k -> new LinkedHashSet<>())
                    .add(r.status());
        }

        assertThat(statusesByCode).isNotEmpty();
        assertThat(statusesByCode).allSatisfy((code, statuses) -> assertThat(statuses)
                .as("code %s is emitted at more than one status: %s", code, statuses)
                .hasSize(1));
    }

    /**
     * AC-3 — {@code CommonGlobalExceptionHandler.validationFailureStatus()}'s unmodified
     * default is 400, which is exactly what masterdata-api.md publishes, so this service
     * adds <b>no</b> override. Asserted against the base's real default rather than
     * assumed from the task text.
     */
    @Test
    @DisplayName("AC-3: 상속된 validationFailureStatus() 기본값이 400 이라 override 불필요")
    void inheritedValidationStatusIsBadRequestSoNoOverrideIsNeeded() {
        assertStatus(handler.handleValidation(beanValidationFailure()),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertStatus(handler.handleIllegalArgument(new IllegalArgumentException("bad argument")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("every VALIDATION_ERROR path is 400 (malformed body, type mismatch, bad argument)")
    void validationErrorIsAlways400() {
        assertStatus(handler.handleMalformedRequest(malformedBody()),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertStatus(handler.handleTypeMismatch(typeMismatch("departmentId")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
        assertStatus(handler.handleIllegalArgument(new IllegalArgumentException("bad argument")),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    @DisplayName("domain codes resolve to their registered status")
    void domainCodes() {
        assertEnvelope(handler.handleDomain(new DomainErrors.MasterdataNotFoundException("nope")),
                HttpStatus.NOT_FOUND, "MASTERDATA_NOT_FOUND");
        assertEnvelope(handler.handleDomain(new DomainErrors.MasterdataDuplicateKeyException("dup")),
                HttpStatus.CONFLICT, "MASTERDATA_DUPLICATE_KEY");
        assertEnvelope(handler.handleDomain(new DomainErrors.MasterdataParentCycleException("cycle")),
                HttpStatus.CONFLICT, "MASTERDATA_PARENT_CYCLE");
    }

    /**
     * <strong>AC-4 code-string collision.</strong> The shared base's own
     * {@code ObjectOptimisticLockingFailureException} arm answers code {@code "CONFLICT"};
     * masterdata-api.md publishes {@code CONCURRENT_MODIFICATION}. This pins the override
     * — remove it and the emitted code silently flips.
     */
    @Test
    @DisplayName("AC-4: optimistic lock → 409 CONCURRENT_MODIFICATION (base 의 CONFLICT 아님)")
    void optimisticLockKeepsErpCodeString() {
        assertStatus(handler.handleJpaOptimisticLock(new OptimisticLockException("version stale")),
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        assertStatus(handler.handleOptimisticLock(
                        new ObjectOptimisticLockingFailureException("Department", "d-1")),
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
     * 500 to a 422. The inherited arm never routes through the table — this test is the
     * tripwire.
     */
    @Test
    @DisplayName("generic Exception → 500 INTERNAL_ERROR without leaking detail")
    void generic() {
        ResponseEntity<ErrorResponse> r = handler.handleGeneral(new RuntimeException("secret crash"));
        assertStatus(r, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
        assertThat(r.getBody().message()).doesNotContain("secret crash");
    }

    // ---------------- AC-2: `details` is populated, not dead ----------------

    /**
     * AC-2 — before TASK-ERP-BE-038 the {@code details} field existed on
     * {@link ApiErrorBody} but no call site ever set it, while masterdata-api.md's
     * DELETE/retire endpoints promised "{@code details} enumerates the referencer kinds".
     * This pins the now-fulfilled promise.
     */
    @Test
    @DisplayName("AC-2: MASTERDATA_REFERENCE_VIOLATION 은 details.referencers 를 싣는다")
    void referenceViolationCarriesDetails() {
        ResponseEntity<ApiErrorBody> r = handler.handleDomain(
                new DomainErrors.MasterdataReferenceViolationException(
                        "Department d-1 is referenced by active employees — cannot retire",
                        Map.of("referencers", List.of("employees", "costCenters"))));

        assertEnvelope(r, HttpStatus.CONFLICT, "MASTERDATA_REFERENCE_VIOLATION");
        assertThat(r.getBody().details())
                .isEqualTo(Map.of("referencers", List.of("employees", "costCenters")));
    }

    @Test
    @DisplayName("AC-2: details 없는 코드는 details 를 null 로 남긴다 (NON_NULL 로 wire 에서 생략)")
    void codesWithoutDetailsLeaveTheFieldNull() {
        ResponseEntity<ApiErrorBody> r =
                handler.handleDomain(new DomainErrors.MasterdataNotFoundException("nope"));
        assertThat(r.getBody().details()).isNull();
    }

    /**
     * Edge case from the task: a {@code details}-less {@link ApiErrorBody} must serialise
     * identically to the shared {@link ErrorResponse}. Asserted under a <b>bare</b>
     * {@code ObjectMapper} on purpose — that is the weakest configuration, so passing here
     * means the equality holds by construction (pre-formatted ISO-8601 {@code timestamp}
     * string) and not because some mapper happened to disable
     * {@code WRITE_DATES_AS_TIMESTAMPS}.
     */
    @Test
    @DisplayName("details 없는 ApiErrorBody 는 ErrorResponse 와 동일하게 직렬화된다 (bare mapper 기준)")
    void detaillessEnvelopeSerialisesLikeErrorResponse() throws Exception {
        ObjectMapper bare = new ObjectMapper();
        ObjectNode fromApiErrorBody = (ObjectNode) bare.valueToTree(
                ApiErrorBody.of("MASTERDATA_NOT_FOUND", "nope", null));
        ObjectNode fromErrorResponse = (ObjectNode) bare.valueToTree(
                ErrorResponse.of("MASTERDATA_NOT_FOUND", "nope"));

        assertThat(fromApiErrorBody.fieldNames()).toIterable()
                .containsExactlyInAnyOrderElementsOf(() -> fromErrorResponse.fieldNames());
        assertThat(fromApiErrorBody.get("timestamp").isTextual())
                .as("timestamp must be an ISO-8601 string, never a JSON number")
                .isTrue();
        assertThat(fromApiErrorBody.get("code")).isEqualTo(fromErrorResponse.get("code"));
        assertThat(fromApiErrorBody.get("message")).isEqualTo(fromErrorResponse.get("message"));
    }

    // ---------------- helpers ----------------

    private record CodeStatus(String code, HttpStatusCode status) {
    }

    private static CodeStatus shared(ResponseEntity<ErrorResponse> r) {
        assertThat(r.getBody()).isNotNull();
        return new CodeStatus(r.getBody().code(), r.getStatusCode());
    }

    private static CodeStatus envelope(ResponseEntity<ApiErrorBody> r) {
        assertThat(r.getBody()).isNotNull();
        return new CodeStatus(r.getBody().code(), r.getStatusCode());
    }

    private static void assertStatus(ResponseEntity<ErrorResponse> r, HttpStatus expected, String code) {
        assertThat(r.getStatusCode()).isEqualTo(expected);
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().code()).isEqualTo(code);
        assertThat(r.getBody().timestamp()).isNotNull();
    }

    private static void assertEnvelope(ResponseEntity<ApiErrorBody> r, HttpStatus expected, String code) {
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
                .thenReturn(List.of(new FieldError("req", "code", "must not be blank")));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(bindingResult);
        return ex;
    }
}
