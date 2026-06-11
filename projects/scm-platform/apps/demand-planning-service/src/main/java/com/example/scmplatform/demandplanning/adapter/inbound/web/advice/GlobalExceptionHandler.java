package com.example.scmplatform.demandplanning.adapter.inbound.web.advice;

import com.example.scmplatform.demandplanning.adapter.inbound.web.dto.ApiErrorBody;
import com.example.scmplatform.demandplanning.domain.error.InvalidSuggestionStateException;
import com.example.scmplatform.demandplanning.domain.error.MappingNotFoundException;
import com.example.scmplatform.demandplanning.domain.error.PolicyNotFoundException;
import com.example.scmplatform.demandplanning.domain.error.SkuSupplierUnmappedException;
import com.example.scmplatform.demandplanning.domain.error.SuggestionNotFoundException;
import lombok.extern.slf4j.Slf4j;
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
