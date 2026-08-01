package com.example.user.presentation.exception;

import com.example.common.persistence.DataIntegrityViolations;
import com.example.web.dto.ErrorResponse;
import com.example.web.exception.AccessDeniedException;
import com.example.web.exception.CommonGlobalExceptionHandler;
import com.example.user.domain.exception.AddressLimitExceededException;
import com.example.user.domain.exception.AddressNotFoundException;
import com.example.user.domain.exception.AlreadyInWishlistException;
import com.example.user.domain.exception.DefaultAddressCannotBeDeletedException;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.user.domain.exception.WishlistAccessDeniedException;
import com.example.user.domain.exception.WishlistItemNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Non-domain arms (404/405/415/malformed-body/validation/missing-param/generic
 * catch-all) come from {@link CommonGlobalExceptionHandler} (ADR-MONO-058 § D2). Only
 * the {@code X-User-Id}-specific header arm and the catch-all's message text differ
 * from the shared default here, so they stay local — as true Java overrides (same
 * method name/signature as the shared class, re-declaring {@code @ExceptionHandler}),
 * not differently-named methods, since Spring's resolver throws {@code
 * IllegalStateException: Ambiguous @ExceptionHandler} if two methods (inherited +
 * locally declared) map the same exact exception type:
 * <ul>
 *   <li>{@link #handleMissingHeader} special-cases the {@code X-User-Id} header into a
 *       401, which the shared handler's generic 400 arm does not do.</li>
 *   <li>{@link #handleGeneral} answers {@code "An internal server error occurred"},
 *       matching this service's own {@link #handleDataIntegrityViolation} non-unique
 *       500 branch, not the shared handler's {@code "An unexpected error occurred"}.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends CommonGlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", e.getMessage()));
    }

    @ExceptionHandler(AlreadyInWishlistException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyInWishlist(AlreadyInWishlistException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("ALREADY_IN_WISHLIST", e.getMessage()));
    }

    @ExceptionHandler(WishlistAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleWishlistAccessDenied(WishlistAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED", e.getMessage()));
    }

    @ExceptionHandler(WishlistItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWishlistItemNotFound(WishlistItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("WISHLIST_ITEM_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(UserProfileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserProfileNotFound(UserProfileNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("USER_PROFILE_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(AddressNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAddressNotFound(AddressNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("ADDRESS_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(AddressLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleAddressLimitExceeded(AddressLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("ADDRESS_LIMIT_EXCEEDED", e.getMessage()));
    }

    @ExceptionHandler(DefaultAddressCannotBeDeletedException.class)
    public ResponseEntity<ErrorResponse> handleDefaultAddressCannotBeDeleted(DefaultAddressCannotBeDeletedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("DEFAULT_ADDRESS_CANNOT_BE_DELETED", e.getMessage()));
    }

    @Override
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException e) {
        if ("X-User-Id".equals(e.getHeaderName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ErrorResponse.of("UNAUTHORIZED", "X-User-Id header is required"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Missing required header: " + e.getHeaderName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Invalid value for parameter: " + e.getName()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        if (DataIntegrityViolations.isUniqueViolation(e)) {
            // A duplicate is a client-visible conflict: the registry's declared catch-all
            // (the wishlist concurrent-duplicate-insert backstop, wishlist-api.md:49).
            log.warn("Unique constraint violation → 409", e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ErrorResponse.of("DATA_INTEGRITY_VIOLATION", "Data integrity violation"));
        }
        // FK / NOT NULL / CHECK violations are SERVER defects, not client conflicts.
        // Deliberately left as 500 so they stay loud in logs and alerting (TASK-MONO-450 AC-1,
        // converging user-service onto the selective mapping TASK-BE-542 wired into the other eight).
        log.error("Non-unique data integrity violation", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An internal server error occurred"));
    }

    @Override
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "An internal server error occurred"));
    }
}
