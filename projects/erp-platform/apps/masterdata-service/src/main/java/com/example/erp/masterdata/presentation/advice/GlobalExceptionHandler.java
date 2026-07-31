package com.example.erp.masterdata.presentation.advice;

import com.example.erp.masterdata.domain.error.MasterdataDomainException;
import com.example.erp.masterdata.presentation.dto.ApiErrorBody;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * Maps erp masterdata domain exceptions to the masterdata-api.md error
 * envelope. The authoritative code→HTTP table lives in masterdata-api.md §
 * Error code → HTTP; {@link #STATUS_BY_CODE} is the exhaustive mechanical
 * mirror.
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-ERP-BE-038)</strong> — the non-domain arms
 * (404 {@code NoResourceFound}/{@code NoHandlerFound}, 405 {@code MethodNotSupported}
 * incl. the RFC 7231 {@code Allow} header, 415 {@code MediaTypeNotSupported}, 400
 * {@code @Valid} / {@code IllegalArgumentException} / malformed-body /
 * missing-parameter, and the catch-all 500) are inherited from
 * {@code libs/java-web-servlet}'s {@link CommonGlobalExceptionHandler} rather than
 * hand-copied here. Three base arms are deliberately <em>overridden</em> below
 * because erp's published contract differs from the base's generic answer.
 *
 * <p><strong>Envelope</strong> — {@link ApiErrorBody} survives as the
 * {@code details}-carrying extension sanctioned by
 * {@code platform/error-handling.md § Error Response Format}, and is used by the one
 * arm whose contract documents {@code details}: the domain dispatch
 * ({@code MASTERDATA_REFERENCE_VIOLATION} enumerates the referencer kinds). Every
 * other arm emits the shared {@link ErrorResponse}. A {@code details}-less
 * {@code ApiErrorBody} serialises identically to {@link ErrorResponse} — both carry a
 * pre-formatted ISO-8601 {@code timestamp} <em>string</em>, so the equality holds by
 * construction rather than by {@code ObjectMapper} configuration.
 *
 * <p><strong>Validation status</strong> — masterdata-api.md publishes
 * {@code 400 VALIDATION_ERROR}, already
 * {@link CommonGlobalExceptionHandler#validationFailureStatus()}'s default; no override.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    /** masterdata-api.md § Error code → HTTP status (verbatim). */
    private static final Map<String, HttpStatus> STATUS_BY_CODE = Map.ofEntries(
            Map.entry("VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
            Map.entry("IDEMPOTENCY_KEY_REQUIRED", HttpStatus.BAD_REQUEST),
            Map.entry("IDEMPOTENCY_KEY_CONFLICT", HttpStatus.CONFLICT),
            Map.entry("MASTERDATA_NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("MASTERDATA_DUPLICATE_KEY", HttpStatus.CONFLICT),
            Map.entry("MASTERDATA_REFERENCE_VIOLATION", HttpStatus.CONFLICT),
            Map.entry("MASTERDATA_PARENT_CYCLE", HttpStatus.CONFLICT),
            Map.entry("MASTERDATA_EFFECTIVE_PERIOD_INVALID", HttpStatus.UNPROCESSABLE_ENTITY),
            Map.entry("PERMISSION_DENIED", HttpStatus.FORBIDDEN),
            Map.entry("DATA_SCOPE_FORBIDDEN", HttpStatus.FORBIDDEN),
            Map.entry("TENANT_FORBIDDEN", HttpStatus.FORBIDDEN),
            Map.entry("EXTERNAL_TRAFFIC_REJECTED", HttpStatus.FORBIDDEN),
            Map.entry("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
            Map.entry("CONCURRENT_MODIFICATION", HttpStatus.CONFLICT),
            Map.entry("IDEMPOTENCY_STORE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry("ILLEGAL_STATE", HttpStatus.UNPROCESSABLE_ENTITY));

    /**
     * Resolve a code's status from {@link #STATUS_BY_CODE} — the single place a
     * code→status pair is decided. Handlers that <i>mint</i> a code (rather than
     * carry one on a {@link MasterdataDomainException}) route through here too, so one
     * code can never leave this service at two different statuses.
     *
     * <p>The inherited catch-all is deliberately NOT routed here: {@code INTERNAL_ERROR}
     * is absent from the table, so the {@code getOrDefault} fallback would turn its
     * 500 into a 422.
     */
    private ResponseEntity<ErrorResponse> respond(String code, String message) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(code, HttpStatus.UNPROCESSABLE_ENTITY);
        return ResponseEntity.status(status).body(ErrorResponse.of(code, message));
    }

    /**
     * The one arm that can carry {@code details} — {@code MASTERDATA_REFERENCE_VIOLATION}
     * enumerates the blocking referencer kinds (masterdata-api.md § retire endpoints).
     * Codes without {@code details} still serialise as {@code {code, message, timestamp}}
     * because {@code @JsonInclude(NON_NULL)} drops the null field.
     */
    @ExceptionHandler(MasterdataDomainException.class)
    public ResponseEntity<ApiErrorBody> handleDomain(MasterdataDomainException e) {
        HttpStatus status = STATUS_BY_CODE.getOrDefault(e.code(),
                HttpStatus.UNPROCESSABLE_ENTITY);
        if (status.is5xxServerError()) {
            log.warn("domain failure {} -> {}: {}", e.code(), status, e.getMessage());
        }
        return ResponseEntity.status(status)
                .body(ApiErrorBody.of(e.code(), e.getMessage(), e.details()));
    }

    /**
     * Overrides the base's generic {@code VALIDATION_ERROR} answer: on a mutating
     * masterdata endpoint a missing {@code Idempotency-Key} is the contract's
     * {@code IDEMPOTENCY_KEY_REQUIRED} (400), not a nameless validation failure.
     * Any other missing header falls back to the base's semantics.
     */
    @Override
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return respond("IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required for mutating endpoints");
        }
        return respond("VALIDATION_ERROR", "Missing required header: " + e.getHeaderName());
    }

    /**
     * Not covered by the shared base — without this arm a malformed path variable /
     * query parameter would fall through to the catch-all and regress the documented
     * 400 into a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return respond("VALIDATION_ERROR", "Invalid parameter: " + e.getName());
    }

    /**
     * <strong>AC-4 code-string collision.</strong> The shared base answers this same
     * exception with code {@code "CONFLICT"}; masterdata-api.md and
     * {@code platform/error-handling.md} § Transactional Trait both publish erp's
     * descriptive alias {@code CONCURRENT_MODIFICATION} for it. Inheriting the base arm
     * unmodified would silently flip the emitted code string, so the arm is overridden
     * rather than adopted (TASK-ERP-BE-038 — deliberate, not accidental).
     */
    @Override
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException e) {
        return respond("CONCURRENT_MODIFICATION",
                "Concurrent modification detected. Please retry.");
    }

    /**
     * JPA's own {@link OptimisticLockException} — a different type from Spring's
     * {@link ObjectOptimisticLockingFailureException} above and absent from the shared
     * base, so it stays a separate local arm. Declaring both types on one
     * {@code @ExceptionHandler} would collide with the (overridden) base arm and fail
     * the advice registration as an ambiguous mapping.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleJpaOptimisticLock(OptimisticLockException e) {
        return respond("CONCURRENT_MODIFICATION",
                "Concurrent modification detected. Please retry.");
    }

    /** Registered as {@code ILLEGAL_STATE} 422; the shared base carries no such arm. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return respond("ILLEGAL_STATE", e.getMessage());
    }
}
