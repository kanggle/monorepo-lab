package com.example.scmplatform.procurement.presentation.advice;

import com.example.common.persistence.DataIntegrityViolations;
import com.example.scmplatform.procurement.domain.error.AsnOverreceiptException;
import com.example.scmplatform.procurement.domain.error.CatalogSkuUnknownException;
import com.example.scmplatform.procurement.domain.error.IdempotencyKeyMismatchException;
import com.example.scmplatform.procurement.domain.error.PoAlreadyConfirmedException;
import com.example.scmplatform.procurement.domain.error.PoNotFoundException;
import com.example.scmplatform.procurement.domain.error.PoQuantityExceededException;
import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
import com.example.scmplatform.procurement.domain.error.SupplierInactiveException;
import com.example.scmplatform.procurement.domain.error.SupplierNotFoundException;
import com.example.scmplatform.procurement.domain.error.SupplierUnavailableException;
import com.example.scmplatform.procurement.presentation.dto.ApiErrorBody;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps procurement domain exceptions to the platform error envelope.
 * Status conventions follow rules/domains/scm.md Standard Error Codes:
 *
 * <ul>
 *   <li>404 — PO_NOT_FOUND, SUPPLIER_NOT_FOUND</li>
 *   <li>409 — CONCURRENT_MODIFICATION (optimistic lock),
 *             CONFLICT (unique-constraint data integrity only)</li>
 *   <li>422 — PO_STATUS_TRANSITION_INVALID, PO_ALREADY_CONFIRMED,
 *             PO_QUANTITY_EXCEEDED, ASN_OVERRECEIPT, SUPPLIER_INACTIVE,
 *             CATALOG_SKU_UNKNOWN, IDEMPOTENCY_KEY_MISMATCH, VALIDATION_ERROR</li>
 *   <li>500 — INTERNAL_ERROR (non-unique data integrity: FK / NOT NULL / CHECK)</li>
 *   <li>503 — SUPPLIER_UNAVAILABLE</li>
 * </ul>
 *
 * <p><strong>ADR-MONO-058 § D2 (TASK-SCM-BE-055)</strong>: the framework/non-domain arms
 * (404 {@code NoResourceFound}/{@code NoHandlerFound}, 405 {@code MethodNotSupported}
 * incl. the RFC 7231 {@code Allow} header, 415 {@code MediaTypeNotSupported},
 * 400 malformed-body / missing-parameter, {@code @Valid} violations, and the catch-all
 * 500) are inherited from {@code libs/java-web-servlet}'s
 * {@link CommonGlobalExceptionHandler} instead of being hand-copied here. Only genuinely
 * procurement-owned policy stays below.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    /**
     * scm publishes <strong>422</strong> for {@code @Valid} constraint violations and for
     * {@code IllegalArgumentException} at the controller boundary
     * ({@code procurement-api.md} § Error codes — "{@code VALIDATION_ERROR} | 400/422"),
     * where the shared default is 400. One override moves both inherited arms; see
     * {@link CommonGlobalExceptionHandler#validationFailureStatus()}.
     */
    @Override
    protected HttpStatus validationFailureStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }

    @ExceptionHandler(PoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePoNotFound(PoNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("PO_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSupplierNotFound(SupplierNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("SUPPLIER_NOT_FOUND", e.getMessage()));
    }

    /**
     * The one arm in this service whose response carries {@code details} — documented in
     * {@code procurement-api.md} as "response includes {@code details: { from, to, actor }}".
     * It therefore returns the {@code details}-carrying {@link ApiErrorBody} extension of
     * the shared envelope rather than {@link ErrorResponse}; every other arm returns the
     * shared type (ADR-MONO-058 § D2 / TASK-SCM-BE-055 design decision 1).
     */
    @ExceptionHandler(PoStatusTransitionInvalidException.class)
    public ResponseEntity<ApiErrorBody> handleStatusInvalid(PoStatusTransitionInvalidException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", e.getFrom().name());
        details.put("to", e.getTo().name());
        details.put("actor", e.getActor().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.withDetails("PO_STATUS_TRANSITION_INVALID",
                        "Invalid PO status transition", details));
    }

    @ExceptionHandler(PoAlreadyConfirmedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyConfirmed(PoAlreadyConfirmedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PO_ALREADY_CONFIRMED", e.getMessage()));
    }

    @ExceptionHandler(PoQuantityExceededException.class)
    public ResponseEntity<ErrorResponse> handleQuantityExceeded(PoQuantityExceededException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PO_QUANTITY_EXCEEDED", e.getMessage()));
    }

    @ExceptionHandler(AsnOverreceiptException.class)
    public ResponseEntity<ErrorResponse> handleOverreceipt(AsnOverreceiptException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ASN_OVERRECEIPT", e.getMessage()));
    }

    @ExceptionHandler(SupplierInactiveException.class)
    public ResponseEntity<ErrorResponse> handleSupplierInactive(SupplierInactiveException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("SUPPLIER_INACTIVE", e.getMessage()));
    }

    @ExceptionHandler(CatalogSkuUnknownException.class)
    public ResponseEntity<ErrorResponse> handleSku(CatalogSkuUnknownException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("CATALOG_SKU_UNKNOWN", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyMismatchException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyMismatch(IdempotencyKeyMismatchException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("IDEMPOTENCY_KEY_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(SupplierUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleSupplierUnavailable(SupplierUnavailableException e) {
        log.warn("supplier unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.of("SUPPLIER_UNAVAILABLE", e.getMessage()));
    }

    /**
     * Spring's optimistic-lock failure. <strong>Overrides</strong> the shared base rather
     * than deleting the arm: the base answers 409 {@code CONFLICT}, while scm procurement
     * deliberately answers 409 {@code CONCURRENT_MODIFICATION} so consumers can pick a
     * retry strategy (CONCURRENT_MODIFICATION = retry OK, CONFLICT = must change state
     * first — TASK-SCM-BE-010, architecture.md Failure Mode #16, and
     * {@code procurement-api.md} § Error codes). Inheriting the base unchanged here would
     * have silently renamed a published error code.
     */
    @Override
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONCURRENT_MODIFICATION",
                        "Concurrent modification detected. Please retry."));
    }

    /**
     * JPA's own {@link OptimisticLockException} — a different type from Spring's
     * {@link ObjectOptimisticLockingFailureException} above, so it needs its own arm.
     * It must carry a <em>distinct method name</em>: two differently-named methods mapped
     * to the same exception type is an "Ambiguous @ExceptionHandler" failure at bean
     * creation, not at compile time.
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleJpaOptimisticLock(OptimisticLockException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("CONCURRENT_MODIFICATION",
                        "Concurrent modification detected. Please retry."));
    }

    /**
     * DB constraint violations that no domain-specific handler claimed. The status is
     * decided at runtime, so this cannot use a static mapping:
     *
     * <ul>
     *   <li><b>Unique violation</b> (SQLSTATE 23505) → 409 {@code CONFLICT}. scm intentionally
     *       treats this as a client-visible conflict — the "must change state first" signal,
     *       distinct from {@code CONCURRENT_MODIFICATION} (which invites an immediate retry).</li>
     *   <li><b>FK / NOT NULL / CHECK</b> violation → 500 {@code INTERNAL_ERROR}, logged at
     *       error. These are SERVER defects (a bug wrote an inconsistent row) and must stay
     *       loud in logs / alerting rather than be masked as a 409 (TASK-MONO-450). Mapping
     *       everything to 409 hid these 500s from monitoring.</li>
     * </ul>
     *
     * <p>Discrimination uses {@link DataIntegrityViolations#isUniqueViolation(Throwable)} — a
     * SQLSTATE walk of the cause chain, because Spring maps every Hibernate
     * {@code ConstraintViolationException} to a plain {@code DataIntegrityViolationException},
     * so the exception <em>type</em> cannot discriminate and the message is vendor-dependent.
     *
     * <p>Not covered by {@link CommonGlobalExceptionHandler} — runtime discrimination is
     * service policy, so this arm stays local.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException e) {
        if (DataIntegrityViolations.isUniqueViolation(e)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("CONFLICT", "Data integrity violation"));
        }
        // FK / NOT NULL / CHECK violations are server defects — keep them loud (same
        // INTERNAL_ERROR envelope as the inherited catch-all).
        log.error("Non-unique data integrity violation", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    /**
     * <strong>Overrides</strong> the shared base's 400 {@code VALIDATION_ERROR} arm to keep
     * procurement's {@code Idempotency-Key} special case: a missing idempotency key on a
     * mutating endpoint is {@code IDEMPOTENCY_KEY_REQUIRED} (T1 /
     * {@code platform/error-handling.md} § Transactional Trait), not a generic missing
     * header. Any other missing header falls back to the base's exact message shape.
     */
    @Override
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorResponse.of("IDEMPOTENCY_KEY_REQUIRED",
                            "Idempotency-Key header is required for mutating endpoints"));
        }
        return super.handleMissingHeader(e);
    }

    /**
     * <strong>Overrides</strong> the shared base only to keep the diagnostic log line —
     * the status and body come straight from {@code super}, i.e. 422 via
     * {@link #validationFailureStatus()}. TASK-SCM-BE-021 added this logging deliberately:
     * a silent 422 was what made TASK-MONO-171 hard to diagnose, so dropping it during
     * adoption would be an undisclosed regression of an incident fix.
     */
    @Override
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("illegal argument at controller boundary", e);
        return super.handleIllegalArgument(e);
    }

    /**
     * Not covered by {@link CommonGlobalExceptionHandler} — without this arm a non-parsable
     * path/query value would fall through to the catch-all and regress the documented 400
     * into a 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    /** Registered as {@code ILLEGAL_STATE} 422 in {@code platform/error-handling.md} § General. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ILLEGAL_STATE", e.getMessage()));
    }

    /**
     * Pass-through for {@link ResponseStatusException} (e.g. webhook signature
     * failures thrown inline in controllers). Without this handler the inherited
     * catch-all intercepts it and returns 500 instead of the intended status code.
     *
     * <p>Service-specific plumbing, not part of the shared base's generic tail — it is
     * the emitter of the {@code REQUEST_ERROR} registry row, whose status is the
     * exception's own by construction.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        String code = status == 401 ? "UNAUTHORIZED"
                : status == 403 ? "PERMISSION_DENIED"
                : "REQUEST_ERROR";
        String reason = e.getReason() != null ? e.getReason() : e.getMessage();
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(code, reason));
    }
}
