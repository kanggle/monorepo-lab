package com.example.scmplatform.procurement.presentation.advice;

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
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
 *   <li>409 — CONFLICT (optimistic lock / data integrity)</li>
 *   <li>422 — PO_STATUS_TRANSITION_INVALID, PO_ALREADY_CONFIRMED,
 *             PO_QUANTITY_EXCEEDED, ASN_OVERRECEIPT, SUPPLIER_INACTIVE,
 *             CATALOG_SKU_UNKNOWN, IDEMPOTENCY_KEY_MISMATCH, VALIDATION_ERROR</li>
 *   <li>503 — SUPPLIER_UNAVAILABLE</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PoNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handlePoNotFound(PoNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("PO_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(SupplierNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleSupplierNotFound(SupplierNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("SUPPLIER_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PoStatusTransitionInvalidException.class)
    public ResponseEntity<ApiErrorBody> handleStatusInvalid(PoStatusTransitionInvalidException e) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", e.getFrom().name());
        details.put("to", e.getTo().name());
        details.put("actor", e.getActor().name());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("PO_STATUS_TRANSITION_INVALID",
                        "Invalid PO status transition", details));
    }

    @ExceptionHandler(PoAlreadyConfirmedException.class)
    public ResponseEntity<ApiErrorBody> handleAlreadyConfirmed(PoAlreadyConfirmedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("PO_ALREADY_CONFIRMED", e.getMessage()));
    }

    @ExceptionHandler(PoQuantityExceededException.class)
    public ResponseEntity<ApiErrorBody> handleQuantityExceeded(PoQuantityExceededException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("PO_QUANTITY_EXCEEDED", e.getMessage()));
    }

    @ExceptionHandler(AsnOverreceiptException.class)
    public ResponseEntity<ApiErrorBody> handleOverreceipt(AsnOverreceiptException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("ASN_OVERRECEIPT", e.getMessage()));
    }

    @ExceptionHandler(SupplierInactiveException.class)
    public ResponseEntity<ApiErrorBody> handleSupplierInactive(SupplierInactiveException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("SUPPLIER_INACTIVE", e.getMessage()));
    }

    @ExceptionHandler(CatalogSkuUnknownException.class)
    public ResponseEntity<ApiErrorBody> handleSku(CatalogSkuUnknownException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("CATALOG_SKU_UNKNOWN", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyKeyMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleIdempotencyMismatch(IdempotencyKeyMismatchException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("IDEMPOTENCY_KEY_MISMATCH", e.getMessage()));
    }

    @ExceptionHandler(SupplierUnavailableException.class)
    public ResponseEntity<ApiErrorBody> handleSupplierUnavailable(SupplierUnavailableException e) {
        log.warn("supplier unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorBody.of("SUPPLIER_UNAVAILABLE", e.getMessage()));
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<ApiErrorBody> handleOptimisticLock(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("CONFLICT", "Concurrent modification detected. Please retry."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorBody> handleIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("CONFLICT", "Data integrity violation"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorBody> handleMissingHeader(MissingRequestHeaderException e) {
        if ("Idempotency-Key".equalsIgnoreCase(e.getHeaderName())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiErrorBody.of("IDEMPOTENCY_KEY_REQUIRED",
                            "Idempotency-Key header is required for mutating endpoints"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR",
                        "Missing required header: " + e.getHeaderName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorBody> handleMalformed(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Malformed request body"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalState(IllegalStateException e) {
        log.warn("illegal state at controller boundary", e);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("ILLEGAL_STATE", e.getMessage()));
    }

    /**
     * Pass-through for {@link ResponseStatusException} (e.g. webhook signature
     * failures thrown inline in controllers). Without this handler the catch-all
     * {@code handleGeneral} intercepts it and returns 500 instead of the
     * intended status code.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorBody> handleResponseStatus(ResponseStatusException e) {
        int status = e.getStatusCode().value();
        String code = status == 401 ? "UNAUTHORIZED"
                : status == 403 ? "PERMISSION_DENIED"
                : "REQUEST_ERROR";
        String reason = e.getReason() != null ? e.getReason() : e.getMessage();
        return ResponseEntity.status(status)
                .body(ApiErrorBody.of(code, reason));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
