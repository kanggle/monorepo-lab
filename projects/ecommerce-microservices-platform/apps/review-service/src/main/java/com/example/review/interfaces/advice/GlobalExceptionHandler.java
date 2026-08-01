package com.example.review.interfaces.advice;

import com.example.review.domain.exception.ProductNotPurchasedException;
import com.example.review.domain.exception.ReviewAccessDeniedException;
import com.example.review.domain.exception.ReviewAlreadyExistsException;
import com.example.review.domain.exception.ReviewNotFoundException;
import com.example.common.persistence.DataIntegrityViolations;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.CommonGlobalExceptionHandler;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Non-domain arms (404/405/415/generic catch-all) come from
 * {@link CommonGlobalExceptionHandler} (ADR-MONO-058 § D2). {@link #handleValidation}
 * (no field-name prefix), {@link #handleMissingHeader} (401 {@code UNAUTHORIZED},
 * header-name-agnostic message), and {@link #handleIllegalArgument} ({@code
 * INVALID_REVIEW_REQUEST} code) all diverge from the shared handler's defaults, so
 * they stay local — each as a true Java override (same name/signature/return type as
 * the shared method, re-declaring {@code @ExceptionHandler}), not a differently-named
 * method, since Spring's resolver throws {@code IllegalStateException: Ambiguous
 * @ExceptionHandler} if two methods (inherited + local) map the same exact exception
 * type.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @Override
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(v -> v.getMessage())
                .findFirst()
                .orElse("Invalid input value");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    @Override
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHORIZED", "Missing or invalid access token"));
    }

    @Override
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("INVALID_REVIEW_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(ReviewNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReviewNotFound(ReviewNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("REVIEW_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(ReviewAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleReviewAlreadyExists(ReviewAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("REVIEW_ALREADY_EXISTS", ex.getMessage()));
    }

    @ExceptionHandler(ProductNotPurchasedException.class)
    public ResponseEntity<ErrorResponse> handleProductNotPurchased(ProductNotPurchasedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("PRODUCT_NOT_PURCHASED", ex.getMessage()));
    }

    @ExceptionHandler(ReviewAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(ReviewAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (DataIntegrityViolations.isUniqueViolation(ex)) {
            // A duplicate is a client-visible conflict: the registry's declared catch-all.
            log.warn("Unique constraint violation → 409", ex);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("DATA_INTEGRITY_VIOLATION", "Data integrity violation"));
        }
        // FK / NOT NULL / CHECK violations are SERVER defects, not client conflicts.
        // Deliberately left as 500 so they stay loud in logs and alerting (TASK-BE-542 AC-1).
        log.error("Non-unique data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
