package com.example.scmplatform.demandplanning.adapter.inbound.web.advice;

import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.ApiErrorBody;
import com.example.scmplatform.demandplanning.domain.error.InvalidSuggestionStateException;
import com.example.scmplatform.demandplanning.domain.error.MappingNotFoundException;
import com.example.scmplatform.demandplanning.domain.error.PolicyNotFoundException;
import com.example.scmplatform.demandplanning.domain.error.ProcurementUnavailableException;
import com.example.scmplatform.demandplanning.domain.error.SkuSupplierUnmappedException;
import com.example.scmplatform.demandplanning.domain.error.SuggestionNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Maps domain exceptions to the scm platform error envelope.
 * Error codes follow demand-planning-api.md additions + rules/domains/scm.md.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SuggestionNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleSuggestionNotFound(SuggestionNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("SUGGESTION_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(PolicyNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handlePolicyNotFound(PolicyNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("POLICY_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MappingNotFoundException.class)
    public ResponseEntity<ApiErrorBody> handleMappingNotFound(MappingNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorBody.of("MAPPING_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(InvalidSuggestionStateException.class)
    public ResponseEntity<ApiErrorBody> handleInvalidState(InvalidSuggestionStateException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("INVALID_SUGGESTION_STATE", e.getMessage()));
    }

    @ExceptionHandler(SkuSupplierUnmappedException.class)
    public ResponseEntity<ApiErrorBody> handleSkuUnmapped(SkuSupplierUnmappedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("SKU_SUPPLIER_UNMAPPED", e.getMessage()));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorBody> handleOptimisticLock(OptimisticLockingFailureException e) {
        // Two operators approving the same suggestion concurrently — one wins the
        // version bump; the loser retries. Procurement idempotency on
        // sourceSuggestionId still guarantees a single PO.
        log.warn("Concurrent modification on suggestion: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorBody.of("CONCURRENT_MODIFICATION",
                        "The suggestion was modified concurrently; retry the request"));
    }

    @ExceptionHandler(ProcurementUnavailableException.class)
    public ResponseEntity<ApiErrorBody> handleProcurementUnavailable(ProcurementUnavailableException e) {
        // Suggestion stays APPROVED (architecture.md failure mode); operator
        // retries — the procurement call is idempotent on sourceSuggestionId.
        log.error("Procurement DRAFT-PO leg unavailable: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorBody.of("PROCUREMENT_UNAVAILABLE",
                        "Procurement is unavailable; the suggestion stays APPROVED — retry"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorBody> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorBody> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorBody.of("VALIDATION_ERROR", "Invalid parameter: " + e.getName()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Rejecting request with 422 VALIDATION_ERROR: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiErrorBody.of("VALIDATION_ERROR", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorBody> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorBody.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
