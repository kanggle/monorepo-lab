package com.example.product.presentation.advice;

import com.example.product.domain.exception.DuplicateVariantOptionException;
import com.example.product.domain.exception.IdempotencyKeyConflictException;
import com.example.product.domain.exception.IdempotencyKeyRequiredException;
import com.example.product.domain.exception.ImageLimitExceededException;
import com.example.product.domain.exception.ImageNotFoundException;
import com.example.product.domain.exception.InsufficientStockException;
import com.example.product.domain.exception.InvalidCategoryException;
import com.example.product.domain.exception.MediaNotFoundException;
import com.example.product.domain.exception.MediaValidationException;
import com.example.product.domain.exception.ProductNotFoundException;
import com.example.product.domain.exception.SellerInvitationAlreadyUsedException;
import com.example.product.domain.exception.SellerInvitationEmailMismatchException;
import com.example.product.domain.exception.SellerInvitationExpiredException;
import com.example.product.domain.exception.SellerInvitationNotFoundException;
import com.example.product.domain.exception.SellerMemberAccountNotEligibleException;
import com.example.product.domain.exception.SellerNotActiveException;
import com.example.product.domain.exception.SellerNotFoundException;
import com.example.product.domain.exception.SellerRoleServiceUnavailableException;
import com.example.product.domain.exception.StorageUnavailableException;
import com.example.product.domain.exception.VariantNotFoundException;
import com.example.common.persistence.DataIntegrityViolations;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.AccessDeniedException;
import com.example.web.exception.CommonGlobalExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Non-domain arms (malformed-body/404/405/415/generic catch-all) come from
 * {@link CommonGlobalExceptionHandler} (ADR-MONO-058 § D2). {@link #handleValidation}
 * stays local — it joins *every* field error (not just the first) with
 * {@code Collectors.joining(", ")}, unlike the shared handler's single-field message,
 * so collapsing it would change client-visible text for multi-field validation
 * failures. It is declared as a true Java override (same name/signature/return type as
 * the shared method, re-declaring {@code @ExceptionHandler}) rather than a
 * differently-named method, since Spring's resolver throws {@code IllegalStateException:
 * Ambiguous @ExceptionHandler} if two methods (inherited + local) map the same exact
 * exception type.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @Override
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message.isEmpty() ? "Validation failed" : message));
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return ErrorResponse.of("ACCESS_DENIED", ex.getMessage());
    }

    @ExceptionHandler(InvalidCategoryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidCategory(InvalidCategoryException ex) {
        return ErrorResponse.of("INVALID_CATEGORY", ex.getMessage());
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex) {
        return ErrorResponse.of("PRODUCT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(VariantNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleVariantNotFound(VariantNotFoundException ex) {
        return ErrorResponse.of("VARIANT_NOT_FOUND", ex.getMessage());
    }

    // ─── seller members (TASK-MONO-752, product-api.md § Seller members) ───────────────────────

    @ExceptionHandler(SellerNotActiveException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSellerNotActive(SellerNotActiveException ex) {
        return ErrorResponse.of("SELLER_NOT_ACTIVE", ex.getMessage());
    }

    @ExceptionHandler(SellerInvitationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSellerInvitationNotFound(SellerInvitationNotFoundException ex) {
        return ErrorResponse.of("SELLER_INVITATION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(SellerInvitationExpiredException.class)
    @ResponseStatus(HttpStatus.GONE)
    public ErrorResponse handleSellerInvitationExpired(SellerInvitationExpiredException ex) {
        return ErrorResponse.of("SELLER_INVITATION_EXPIRED", ex.getMessage());
    }

    @ExceptionHandler(SellerInvitationAlreadyUsedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSellerInvitationAlreadyUsed(SellerInvitationAlreadyUsedException ex) {
        return ErrorResponse.of("SELLER_INVITATION_ALREADY_USED", ex.getMessage());
    }

    @ExceptionHandler(SellerInvitationEmailMismatchException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleSellerInvitationEmailMismatch(SellerInvitationEmailMismatchException ex) {
        return ErrorResponse.of("SELLER_INVITATION_EMAIL_MISMATCH", ex.getMessage());
    }

    @ExceptionHandler(SellerMemberAccountNotEligibleException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleSellerMemberAccountNotEligible(SellerMemberAccountNotEligibleException ex) {
        return ErrorResponse.of("SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE", ex.getMessage());
    }

    @ExceptionHandler(SellerRoleServiceUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleSellerRoleServiceUnavailable(SellerRoleServiceUnavailableException ex) {
        return ErrorResponse.of("SERVICE_UNAVAILABLE", ex.getMessage());
    }

    @ExceptionHandler(SellerNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleSellerNotFound(SellerNotFoundException ex) {
        return ErrorResponse.of("SELLER_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInsufficientStock(InsufficientStockException ex) {
        return ErrorResponse.of("INSUFFICIENT_STOCK", ex.getMessage());
    }

    @ExceptionHandler(ImageNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleImageNotFound(ImageNotFoundException ex) {
        return ErrorResponse.of("IMAGE_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ImageLimitExceededException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleImageLimitExceeded(ImageLimitExceededException ex) {
        return ErrorResponse.of("IMAGE_LIMIT_EXCEEDED", ex.getMessage());
    }

    @ExceptionHandler(MediaNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleMediaNotFound(MediaNotFoundException ex) {
        return ErrorResponse.of("MEDIA_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(MediaValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMediaValidation(MediaValidationException ex) {
        return ErrorResponse.of("MEDIA_VALIDATION_FAILED", ex.getMessage());
    }

    @ExceptionHandler(StorageUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handleStorageUnavailable(StorageUnavailableException ex) {
        log.error("Storage unavailable: {}", ex.getMessage(), ex);
        return ErrorResponse.of("STORAGE_UNAVAILABLE", "Object storage service is unavailable");
    }

    @ExceptionHandler(DuplicateVariantOptionException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleDuplicateVariantOption(DuplicateVariantOptionException ex) {
        return ErrorResponse.of("DUPLICATE_VARIANT_OPTION", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyRequiredException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIdempotencyKeyRequired(IdempotencyKeyRequiredException ex) {
        return ErrorResponse.of("IDEMPOTENCY_KEY_REQUIRED", ex.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleIdempotencyKeyConflict(IdempotencyKeyConflictException ex) {
        return ErrorResponse.of("IDEMPOTENCY_KEY_CONFLICT", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleOptimisticLocking(OptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return ErrorResponse.of("CONFLICT", "Concurrent modification conflict. Please try again.");
    }

    /**
     * Backstop for DB constraint violations no domain-specific handler claimed. Unlike most
     * handlers in this class this cannot use {@code @ResponseStatus}, because the status is
     * decided at runtime (409 for a unique violation, 500 otherwise), so it returns
     * {@link ResponseEntity}.
     */
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
